import type { Locale } from "@/lib/i18n";

export function formatDateTime(value: string, locale?: Locale) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatMovementType(value: "inbound" | "outbound" | "adjustment" | "transfer", locale: Locale) {
  switch (value) {
    case "inbound":
      return locale === "zh-CN" ? "入库" : "Inbound";
    case "outbound":
      return locale === "zh-CN" ? "出库" : "Outbound";
    case "adjustment":
      return locale === "zh-CN" ? "调整" : "Adjustment";
    case "transfer":
      return locale === "zh-CN" ? "转移" : "Transfer";
  }
}
