export class ReadTimeoutError extends Error {}

// AbortController also works on HTTP NAS pages without newer timeout helpers.
export async function readJsonWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs = 15_000,
): Promise<{ response: Response; payload: unknown }> {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
  try {
    const response = await fetch(url, { ...init, signal: controller.signal });
    // HTTP failures need their status, even when a proxy returns an HTML error.
    const payload: unknown = response.ok ? await response.json() : null;
    return { response, payload };
  } catch (error) {
    if (timedOut) throw new ReadTimeoutError("API read timed out");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
