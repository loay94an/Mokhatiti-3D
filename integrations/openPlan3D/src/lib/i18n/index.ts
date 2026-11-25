import { derived, writable } from 'svelte/store';
import { en } from './locales/en';
import { pt } from './locales/pt';
import { ar } from './locales/ar';

export type Locale = 'en' | 'pt' | 'ar';
export type TranslationKey = keyof typeof en;
const dictionaries: Record<'en' | 'pt', Record<TranslationKey, string>> = { en, pt };
const preference = writable<Locale>('ar');
const isLocale = (value: unknown): value is Locale => value === 'en' || value === 'pt' || value === 'ar';

export function translate(language: Locale, key: TranslationKey, variables: Record<string, string | number> = {}): string {
  // Arabic is completed in reviewed batches; untranslated keys retain the source wording.
  const message = language === 'ar' ? (ar[key] ?? en[key]) : dictionaries[language][key];
  // Single-pass substitution preserves literal braces in user-provided values.
  return message.replace(/\{(\w+)\}/g, (token, name) =>
    Object.hasOwn(variables, name) ? String(variables[name]) : token);
}

export const locale = {
  subscribe: preference.subscribe,
  set(value: Locale) {
    if (!isLocale(value)) return;
    preference.set(value);
    if (typeof document !== 'undefined') {
      document.documentElement.lang = value;
      document.documentElement.dir = value === 'ar' ? 'rtl' : 'ltr';
    }
    try { localStorage.setItem('o3d_locale', value); } catch { /* In-memory choice still works. */ }
  },
};

/** Run after hydration; Arabic is the first-visit language and saved choices remain respected. */
export function initializeLocale() {
  let saved: unknown;
  try { saved = localStorage.getItem('o3d_locale'); } catch { /* Storage can be disabled. */ }
  locale.set(isLocale(saved) ? saved : 'ar');
}

/** Svelte's $t subscription updates labels without remounting dialogs or inputs. */
export const t = derived(preference, (language) =>
  (key: TranslationKey, variables?: Record<string, string | number>) => translate(language, key, variables));
