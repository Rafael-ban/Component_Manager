import assert from "node:assert/strict";
import test from "node:test";

import { displayCategory } from "./category-display.ts";

test("displayCategory maps a confirmed official English leaf", () => {
  assert.equal(
    displayCategory("Integrated Circuits (ICs)/Power Management (PMIC)/Voltage Regulators - Linear, Low Drop Out (LDO) Regulators"),
    "线性稳压器（LDO）",
  );
});

test("displayCategory preserves Chinese and unknown user categories", () => {
  assert.equal(displayCategory("电阻器/贴片电阻"), "电阻器/贴片电阻");
  assert.equal(displayCategory("My Workshop/Calibrated Parts"), "My Workshop/Calibrated Parts");
});
