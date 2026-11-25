import { get } from 'svelte/store';
import { locale, translate, type TranslationKey } from '$lib/i18n';

const tr = (key: TranslationKey) => translate(get(locale), key);

/** Keep the experimental loader self-contained and bounded before decoding. */
export function validateLocalGLB(buffer: ArrayBuffer): void {
  if (buffer.byteLength > 128 * 1024 * 1024) throw new Error(tr('renderLab.fileTooLarge'));
  if (buffer.byteLength < 20) throw new Error(tr('renderLab.errorIncompleteGlb'));
  const view = new DataView(buffer);
  if (view.getUint32(0, true) !== 0x46546c67 || view.getUint32(4, true) !== 2 || view.getUint32(8, true) !== buffer.byteLength) {
    throw new Error(tr('renderLab.errorValidGlb'));
  }
  const length = view.getUint32(12, true);
  if (view.getUint32(16, true) !== 0x4e4f534a || length > 16 * 1024 * 1024 || length + 20 > buffer.byteLength) {
    throw new Error(tr('renderLab.errorMetadata'));
  }
  let json: any;
  try {
    json = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 20, length)));
  } catch {
    throw new Error(tr('renderLab.errorJson'));
  }
  for (const entry of [...(json.buffers ?? []), ...(json.images ?? [])]) {
    if (entry.uri && !/^data:/.test(entry.uri)) throw new Error(tr('renderLab.errorEmbeddedAssets'));
  }
  const count = (json.meshes ?? []).reduce((total: number, mesh: { primitives?: { attributes?: { POSITION?: number } }[] }) =>
    total + (mesh.primitives ?? []).reduce((sum, primitive) => sum + (json.accessors?.[primitive.attributes?.POSITION ?? -1]?.count ?? 0), 0), 0);
  if (!Number.isFinite(count) || count > 2_000_000) throw new Error(tr('renderLab.errorVertexLimit'));
}
