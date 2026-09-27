import assert from "node:assert/strict";
import test from "node:test";

import { canRetryEnrichment, displayEnrichmentReason, trackedJobAfterRecovery } from "./spec-enrichment.ts";

test("active account job restores tracking when local id is absent or stale", () => {
  const active = { id: "job-current" };
  assert.deepEqual(trackedJobAfterRecovery(active, null), { id: "job-current", job: active });
  assert.deepEqual(trackedJobAfterRecovery(active, "job-stale"), { id: "job-current", job: active });
  assert.deepEqual(trackedJobAfterRecovery(null, null), { id: null, job: null });
});

test("retry includes cancelled unprocessed work and completed failures", () => {
  assert.equal(canRetryEnrichment({ state: "cancelled", total: 10, processed: 4, failed: 0 }), true);
  assert.equal(canRetryEnrichment({ state: "completed", total: 10, processed: 10, failed: 2 }), true);
  assert.equal(canRetryEnrichment({ state: "completed", total: 10, processed: 10, failed: 0 }), false);
  assert.equal(canRetryEnrichment({ state: "running", total: 10, processed: 4, failed: 2 }), false);
});

test("known result reasons translate without inventing translations for exceptions", () => {
  assert.equal(displayEnrichmentReason("Catalog did not provide explicit parameters.", "zh-CN"), "官方资料没有明确参数。");
  assert.equal(displayEnrichmentReason("ValueError: unusual source", "zh-CN"), "ValueError: unusual source");
  assert.equal(displayEnrichmentReason("No exact catalog match for this LCSC SKU.", "en"), "No exact catalog match for this LCSC SKU.");
});
