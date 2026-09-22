export async function copyText(value: string): Promise<boolean> {
  if (!value.trim()) return false;
  if (navigator.clipboard?.writeText) {
    try { await navigator.clipboard.writeText(value); return true; } catch { /* LAN HTTP fallback. */ }
  }
  const input = document.createElement("textarea");
  input.value = value;
  input.style.position = "fixed";
  input.style.opacity = "0";
  document.body.appendChild(input);
  try { input.select(); return document.execCommand("copy"); }
  catch { return false; }
  finally { input.remove(); }
}

export function serverSetupUrl(apiBaseUrl: string): string | undefined {
  try {
    const url = new URL(apiBaseUrl);
    if (!["http:", "https:"].includes(url.protocol)) return undefined;
    url.pathname = url.pathname.replace(/\/+$/, "") + "/setup";
    url.search = ""; url.hash = "";
    return url.toString();
  } catch { return undefined; }
}
