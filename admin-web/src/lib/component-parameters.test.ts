import assert from "node:assert/strict";
import test from "node:test";

import { displayParameterLabel, parseComponentParameters } from "./component-parameters.ts";

test("reads Windows and Android parameter notes with the same mapped result", () => {
  const windows = "型号：FRH0603B1002TS\n参数·Resistance：10kΩ\n参数·Tolerance：±0.1%\n参数·Power(Watts)：100mW";
  const android = "型号：FRH0603B1002TS\n参数：Resistance：10kΩ\n参数：Tolerance：±0.1%\n参数：Power(Watts)：100mW";
  const expected = [
    { label: "阻值", value: "10kΩ" },
    { label: "精度", value: "±0.1%" },
    { label: "额定功率", value: "100mW" },
  ];
  assert.deepEqual(parseComponentParameters(windows), expected);
  assert.deepEqual(parseComponentParameters(android), expected);
});

test("maps common capacitor and inductor fields, preserving unknown official labels", () => {
  assert.deepEqual(parseComponentParameters(
    "参数·Capacitance：100uF\n参数·Voltage Rating：6.3V\n参数·Inductance：1.5uH\n参数·DC Resistance(DCR)：78mΩ\n参数·Custom Frequency：120MHz\n参数·Package：0603",
  ), [
    { label: "容量", value: "100uF" }, { label: "耐压", value: "6.3V" },
    { label: "电感量", value: "1.5uH" }, { label: "直流电阻", value: "78mΩ" },
    { label: "Custom Frequency", value: "120MHz" },
  ]);
});

test("ignores narrative text and incomplete parameter lines without guessing", () => {
  assert.deepEqual(parseComponentParameters("官方说明：10kΩ ±1%\n型号：R0603\n参数·阻值：\n参数：：10kΩ"), []);
  assert.deepEqual(parseComponentParameters(null), []);
});

test("key units remain visible in values with localized labels", () => {
  const parameters = parseComponentParameters(
    "参数·额定电压(V)：50\n参数：Voltage Rating(V)：50V\n参数·额定功率(W)：0.1",
  );
  assert.deepEqual(parameters, [
    { label: "耐压", value: "50V" },
    { label: "额定功率", value: "0.1W" },
  ]);
  assert.equal(displayParameterLabel(parameters[0].label, "zh-CN"), "耐压");
  assert.equal(displayParameterLabel(parameters[0].label, "en"), "Voltage rating");
  assert.equal(displayParameterLabel(parameters[1].label, "en"), "Power rating");
});
