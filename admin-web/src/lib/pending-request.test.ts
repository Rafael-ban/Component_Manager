import assert from "node:assert/strict";
import test from "node:test";

import { pendingStorageKey, requestForPayload, savePendingRequest, shouldRetainPending, stablePayload } from "./pending-request.ts";

test("stablePayload is independent of object key insertion order", () => {
  assert.equal(stablePayload({ sku: "C1", quantity: 2 }), stablePayload({ quantity: 2, sku: "C1" }));
});

test("requestForPayload reuses the id only for an identical retry", () => {
  let sequence = 0;
  const createId = () => `request-${++sequence}`;
  const first = requestForPayload(null, { quantity: 2, note: null }, createId);
  const retry = requestForPayload(first, { note: null, quantity: 2 }, createId);
  const edited = requestForPayload(retry, { note: null, quantity: 3 }, createId);

  assert.equal(retry.requestId, first.requestId);
  assert.notEqual(edited.requestId, first.requestId);
});

test("pending keys isolate servers and component actions", () => {
  assert.notEqual(
    pendingStorageKey("http://server-a:8787", "movement:part-1"),
    pendingStorageKey("http://server-b:8787", "movement:part-1"),
  );
  assert.notEqual(
    pendingStorageKey("http://server-a:8787", "movement:part-1"),
    pendingStorageKey("http://server-a:8787", "movement:part-2"),
  );
});

test("pending keys isolate accounts sharing a server", () => {
  assert.notEqual(pendingStorageKey("https://api.example", "movement:1", "account-a"), pendingStorageKey("https://api.example", "movement:1", "account-b"));
});

test("storage failure is reported without throwing", () => {
  assert.equal(savePendingRequest("unavailable", { requestId: "request-1", payload: "{}" }), false);
});

test("only uncertain transport and server failures retain a pending mutation", () => {
  assert.equal(shouldRetainPending(0), true);
  assert.equal(shouldRetainPending(500), true);
  assert.equal(shouldRetainPending(503), true);
  for (const status of [400, 401, 403, 404, 409, 422]) assert.equal(shouldRetainPending(status), false);
});
