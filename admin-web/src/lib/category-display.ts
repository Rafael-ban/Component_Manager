const officialLeaves: Record<string, string> = {
  "chip resistor - surface mount": "贴片电阻",
  "ceramic capacitors": "陶瓷电容器",
  "aluminum electrolytic capacitors": "铝电解电容器",
  "ferrite beads and chips": "磁珠",
  microcontrollers: "微控制器",
  "voltage regulators - linear": "线性稳压器",
  "voltage regulators - linear, low drop out (ldo) regulators": "线性稳压器（LDO）",
  "ldo regulators": "线性稳压器（LDO）",
  "dc dc switching regulators": "DC-DC开关稳压器",
  "operational amplifiers": "运算放大器",
  mosfets: "MOS管",
  "led indication - discrete": "LED指示器件",
  "temperature sensors": "温度传感器",
  "headers, male pins": "排针",
  "rectangular connectors - housings": "矩形连接器外壳",
};

export function displayCategory(value: string): string {
  const normalized = value.trim();
  if (!normalized || /[\u4e00-\u9fff]/u.test(normalized)) return normalized;
  const leaf = normalized.split("/").at(-1)?.trim().toLowerCase();
  return leaf ? officialLeaves[leaf] ?? normalized : normalized;
}
