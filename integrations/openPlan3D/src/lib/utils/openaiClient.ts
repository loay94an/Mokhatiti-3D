import { get } from 'svelte/store';
import { locale, translate, type TranslationKey } from '$lib/i18n';

const tr = (key: TranslationKey, variables?: Record<string, string | number>) => translate(get(locale), key, variables);

/** Browser-to-provider transport. Never retry paid requests or route through our hosting. */
export interface OpenAIConfig {
  apiKey?: string | null;
  baseUrl?: string;
  model?: string;
}

export const DEFAULT_OPENAI_BASE_URL = 'https://api.openai.com/v1';
export const DEFAULT_OPENAI_MODEL = 'gpt-4.1';
export const OPENAI_MODEL_SUGGESTIONS = ['gpt-4.1', 'gpt-4.1-mini', 'gpt-5.2'];

export function normalizeBaseUrl(value?: string): string {
  const input = value?.trim() || DEFAULT_OPENAI_BASE_URL;
  let url: URL;
  try { url = new URL(input); } catch { throw new Error(tr('provider.errorAbsoluteUrl')); }
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback)) {
    throw new Error(tr('provider.errorHttps'));
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error(tr('provider.errorCredentials'));
  }
  return url.href.replace(/\/+$/, '');
}

export function getEffectiveModel(config: OpenAIConfig): string {
  return config.model?.trim() || DEFAULT_OPENAI_MODEL;
}

export function validateOpenAIConfig(config: OpenAIConfig): string {
  const baseUrl = normalizeBaseUrl(config.baseUrl);
  if (typeof window !== 'undefined' && new URL(baseUrl).origin === window.location.origin) {
    throw new Error(tr('provider.errorNoProxy'));
  }
  if (new URL(baseUrl).hostname === 'api.openai.com' && !config.apiKey?.trim()) {
    throw new Error(tr('provider.errorApiKey'));
  }
  if (new URL(baseUrl).hostname === 'api.openai.com' && /^(gpt-image-|chatgpt-image-|dall-e-)/.test(getEffectiveModel(config))) {
    throw new Error(tr('provider.errorResponsesModel'));
  }
  return baseUrl;
}

export function parseModelsResponse(json: unknown): string[] {
  const list = Array.isArray(json) ? json : isRecord(json) && Array.isArray(json.data) ? json.data : null;
  if (!list) throw new Error(tr('provider.errorModelList'));
  const ids = list.map(item => typeof item === 'string' ? item : isRecord(item) ? item.id : null)
    .filter((id): id is string => typeof id === 'string' && !!id.trim() && id.length <= 256)
    .map(id => id.trim());
  return [...new Set(ids)].sort((a, b) => a.localeCompare(b));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

async function readJSON(response: Response, maxBytes: number): Promise<unknown> {
  if (Number(response.headers.get('content-length')) > maxBytes) {
    await response.body?.cancel();
    throw new Error(tr('provider.errorTooLarge'));
  }
  const reader = response.body?.getReader();
  if (!reader) throw new Error(tr('provider.errorEmpty'));
  const decoder = new TextDecoder();
  let text = '', bytes = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > maxBytes) {
        await reader.cancel();
        throw new Error(tr('provider.errorTooLarge'));
      }
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
  } finally { reader.releaseLock(); }
  try { return JSON.parse(text); } catch { throw new Error(tr('provider.errorMalformedJson')); }
}

async function request(
  config: OpenAIConfig, path: 'models' | 'responses', body: unknown,
  customFetch: typeof fetch, signal?: AbortSignal
): Promise<unknown> {
  const baseUrl = validateOpenAIConfig(config);
  const controller = new AbortController();
  const abort = () => controller.abort();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, path === 'models' ? 30_000 : 180_000);
  signal?.addEventListener('abort', abort, { once: true });
  if (signal?.aborted) abort();
  try {
    controller.signal.throwIfAborted();
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body) headers['Content-Type'] = 'application/json';
    if (config.apiKey?.trim()) headers.Authorization = `Bearer ${config.apiKey.trim()}`;
    const response = await customFetch(`${baseUrl}/${path}`, {
      method: body ? 'POST' : 'GET', headers, body: body ? JSON.stringify(body) : undefined,
      credentials: 'omit', referrerPolicy: 'no-referrer', redirect: 'error', cache: 'no-store',
      signal: controller.signal,
    });
    if (!response.ok) {
      await response.body?.cancel();
      const hintKey: TranslationKey = response.status === 401 || response.status === 403 ? 'provider.errorUnauthorized'
        : response.status === 429 ? 'provider.errorRateLimit'
        : response.status === 400 || response.status === 404 || response.status === 422
          ? 'provider.errorBadRequest' : 'provider.errorStatus';
      throw new Error(`${tr('provider.errorRequestFailed', { status: response.status })} ${tr(hintKey)}`);
    }
    return await readJSON(response, path === 'models' ? 1024 * 1024 : 24 * 1024 * 1024);
  } catch (error) {
    if (timedOut) throw new Error(tr('provider.errorTimeout'));
    if (signal?.aborted) throw new Error(tr('provider.errorCancelled'));
    if (error instanceof TypeError) throw new Error(tr('provider.errorConnection'));
    throw error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}

export async function fetchOpenAIModels(config: OpenAIConfig, customFetch: typeof fetch = fetch, signal?: AbortSignal): Promise<string[]> {
  return parseModelsResponse(await request({ ...config, model: DEFAULT_OPENAI_MODEL }, 'models', undefined, customFetch, signal));
}

export async function generateOpenAIRenderImage(
  config: OpenAIConfig, base64Image: string, prompt: string,
  customFetch: typeof fetch = fetch, signal?: AbortSignal
): Promise<string> {
  const data = await request(config, 'responses', {
    model: getEffectiveModel(config), store: false,
    input: [{ role: 'user', content: [
      { type: 'input_image', image_url: `data:image/png;base64,${base64Image}` },
      { type: 'input_text', text: prompt },
    ] }],
    tools: [{ type: 'image_generation', quality: 'high', size: '1536x1024', output_format: 'png' }],
    tool_choice: { type: 'image_generation' },
  }, customFetch, signal);
  const output = isRecord(data) && Array.isArray(data.output) ? data.output : [];
  const image = output.find(item => isRecord(item) && item.type === 'image_generation_call' && typeof item.result === 'string' && item.result);
  if (image && typeof image.result === 'string' && /^[A-Za-z0-9+/]+={0,2}$/.test(image.result)) {
    return `data:image/png;base64,${image.result}`;
  }
  throw new Error(tr('provider.errorNoImage'));
}
