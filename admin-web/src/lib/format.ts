export function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatMovementType(value: "inbound" | "outbound" | "adjustment") {
  switch (value) {
    case "inbound":
      return "Inbound";
    case "outbound":
      return "Outbound";
    case "adjustment":
      return "Adjustment";
  }
}
