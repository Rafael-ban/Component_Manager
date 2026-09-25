import assert from "node:assert/strict";
import test from "node:test";
import { LANGUAGE_KEY, browserLocale, loadLanguagePreference, loadLanguagePreferenceFrom, resolveLocale, saveLanguagePreference, saveLanguagePreferenceFrom } from "./locale.ts";

test("system language follows the browser and explicit choice overrides it", () => {
  assert.equal(browserLocale("zh-HK"), "zh-CN");
  assert.equal(browserLocale("fr-FR"), "en");
  assert.equal(resolveLocale("system", "zh-TW"), "zh-CN");
  assert.equal(resolveLocale("en", "zh-CN"), "en");
  assert.equal(resolveLocale("zh-CN", "en-US"), "zh-CN");
});

test("language preference persists and invalid stored values fall back to system", () => {
  const values = new Map<string, string>();
  const storage = {
    getItem(key: string) { return values.get(key) ?? null; },
    setItem(key: string, value: string) { values.set(key, value); },
  };
  assert.equal(loadLanguagePreference(storage), "system");
  saveLanguagePreference(storage, "en");
  assert.equal(values.get(LANGUAGE_KEY), "en");
  assert.equal(loadLanguagePreference(storage), "en");
  values.set(LANGUAGE_KEY, "other");
  assert.equal(loadLanguagePreference(storage), "system");
});

test("blocked browser storage leaves a usable system preference", () => {
  const blocked = { getItem(): string | null { throw new Error("blocked"); }, setItem(): void { throw new Error("blocked"); } };
  assert.equal(loadLanguagePreference(blocked), "system");
  assert.doesNotThrow(() => saveLanguagePreference(blocked, "zh-CN"));
  const blockedGetter = () => { throw new Error("storage getter blocked"); };
  assert.equal(loadLanguagePreferenceFrom(blockedGetter), "system");
  assert.doesNotThrow(() => saveLanguagePreferenceFrom(blockedGetter, "en"));
});
