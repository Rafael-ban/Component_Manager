import assert from "node:assert/strict";
import { test } from "node:test";
import { readJsonWithTimeout, ReadTimeoutError } from "./read-json.ts";

test("unreachable API read times out and can be retried", async (t) => {
  t.mock.method(globalThis, "fetch", (_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
    init.signal!.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
  }));
  await assert.rejects(readJsonWithTimeout("http://api.test/auth/me", {}, 5), ReadTimeoutError);
  t.mock.restoreAll();
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({ account_id: "admin" })));
  assert.deepEqual((await readJsonWithTimeout("http://api.test/auth/me", {})).payload, { account_id: "admin" });
});

test("proxy HTML failures retain their status without parsing JSON", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("<html>Not found</html>", { status: 404 }));
  const result = await readJsonWithTimeout("http://api.test/auth/me", {});
  assert.equal(result.response.status, 404);
  assert.equal(result.payload, null);
});

test("network rejection stays distinguishable from timeout", async (t) => {
  const failure = new TypeError("Failed to fetch");
  t.mock.method(globalThis, "fetch", async () => { throw failure; });
  await assert.rejects(readJsonWithTimeout("http://api.test/auth/me", {}), (error) => error === failure);
});
