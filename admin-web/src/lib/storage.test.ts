import assert from "node:assert/strict";
import test from "node:test";
import { loadStoredSession, saveStoredSession, clearStoredSession } from "./storage.ts";

test("legacy stored credentials are removed and only the API address survives", () => {
  const values = new Map<string, string>();
  Object.defineProperty(globalThis, "window", { configurable: true, value: { localStorage: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  }}});
  values.set("component-vault-admin-session", JSON.stringify({apiBaseUrl:"http://server:8787",token:"old-private-key"}));
  assert.deepEqual(loadStoredSession(), {apiBaseUrl:"http://server:8787"});
  assert.ok(!JSON.stringify([...values.values()]).includes("old-private-key"));
  saveStoredSession({apiBaseUrl:"https://api.example.com"});
  assert.deepEqual(loadStoredSession(), {apiBaseUrl:"https://api.example.com"});
  clearStoredSession(); assert.equal(loadStoredSession(), null);
});
test("blocked localStorage does not prevent cookie-based login", () => {
  Object.defineProperty(globalThis, "window", { configurable: true, get() { throw new Error("blocked"); } });
  assert.equal(loadStoredSession(), null);
  assert.doesNotThrow(() => saveStoredSession({apiBaseUrl:"https://api.example.com"}));
  assert.doesNotThrow(clearStoredSession);
});
