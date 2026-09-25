export type LanguagePreference = "system" | "zh-CN" | "en";
export type Locale = "zh-CN" | "en";
export const LANGUAGE_KEY = "component-vault.language";

export function browserLocale(language: string): Locale {
  return language.toLowerCase().startsWith("zh") ? "zh-CN" : "en";
}

export function parseLanguagePreference(value: string | null): LanguagePreference {
  return value === "zh-CN" || value === "en" ? value : "system";
}

export function resolveLocale(preference: LanguagePreference, language: string): Locale {
  return preference === "system" ? browserLocale(language) : preference;
}

export function loadLanguagePreference(storage: Pick<Storage, "getItem">): LanguagePreference {
  try { return parseLanguagePreference(storage.getItem(LANGUAGE_KEY)); }
  catch { return "system"; }
}

export function loadLanguagePreferenceFrom(getStorage: () => Pick<Storage, "getItem">): LanguagePreference {
  try { return loadLanguagePreference(getStorage()); }
  catch { return "system"; }
}

export function saveLanguagePreference(storage: Pick<Storage, "setItem">, preference: LanguagePreference): void {
  try { storage.setItem(LANGUAGE_KEY, preference); }
  catch { /* Storage can be unavailable. */ }
}

export function saveLanguagePreferenceFrom(getStorage: () => Pick<Storage, "setItem">, preference: LanguagePreference): void {
  try { saveLanguagePreference(getStorage(), preference); }
  catch { /* Accessing browser storage can itself be blocked. */ }
}
