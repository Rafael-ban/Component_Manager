import assert from "node:assert/strict";
import test from "node:test";

import { createRequestId } from "./request-id.ts";

test("createRequestId uses getRandomValues when randomUUID is unavailable", () => {
  const value = createRequestId({
    getRandomValues(array) {
      const bytes = array as unknown as Uint8Array;
      bytes.forEach((_, index) => { bytes[index] = index; });
      return array;
    },
  });
  assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test("createRequestId does not crash when Web Crypto is absent", () => {
  assert.match(createRequestId(null), /^request-/);
});
