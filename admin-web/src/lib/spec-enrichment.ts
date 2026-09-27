const knownReasons: Record<string, string> = {
  "No exact catalog match for this LCSC SKU.": "官方目录未找到该立创 SKU 的精确匹配。",
  "Catalog did not provide explicit parameters.": "官方资料没有明确参数。",
  "Component changed during catalog lookup; retry after review.": "查询期间元件已变化，请核对后重试。",
  "Component is deleted or already has parameter notes.": "元件已删除或已有参数记录。",
  "No missing catalog notes.": "没有需要补充的官方资料。",
};

export function displayEnrichmentReason(reason: string | null, locale: string): string {
  if (!reason) return "";
  return locale === "zh-CN" ? knownReasons[reason] ?? reason : reason;
}

export function canRetryEnrichment(job: { state: string; total: number; processed: number; failed: number }): boolean {
  return (job.state === "completed" || job.state === "cancelled")
    && (job.failed > 0 || (job.state === "cancelled" && job.processed < job.total));
}

export function trackedJobAfterRecovery<T extends { id: string }>(
  active: T | null, storedId: string | null,
): { id: string | null; job: T | null } {
  return active ? { id: active.id, job: active } : { id: storedId, job: null };
}
