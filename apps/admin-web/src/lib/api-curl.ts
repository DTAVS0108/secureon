import type { IntegrationApiEndpoint } from "@/lib/integration-api-catalog";

/**
 * A ready-to-run curl command (bash / zsh / Git Bash) for an API reference entry, built from its method,
 * URL, authentication, content type and request example. Entries with several variants set `curl` instead.
 */
export function curlFor(ep: IntegrationApiEndpoint, clientId = "{clientId}"): string {
  if (ep.curl) return ep.curl;

  const lines: string[] = [];
  const auth = ep.auth ?? "";
  const browser = /browser/i.test(auth);
  if (browser) {
    lines.push("# Normally opened in a browser; curl shows the response or the redirect (Location header).");
  }

  const required = (ep.queryParams ?? []).filter((p) => p.required);
  const url = required.length
    ? `${ep.path}?${required.map((p) => `${p.name}=${QUERY_EXAMPLES[p.name] ?? `{${p.name}}`}`).join("&")}`
    : ep.path;

  const parts = [ep.method === "GET" ? `curl -i "${url}"` : `curl -i -X ${ep.method} "${url}"`];

  const header = auth.match(/Authorization:\s*([^(]+?)\s*(?:\(|$)/);
  if (header) parts.push(`-H "Authorization: ${header[1].trim()}"`);
  if (/sets session cookie/i.test(auth)) parts.push("-c cookies.txt");
  else if (/session cookie/i.test(auth) && !browser) parts.push("-b cookies.txt");
  if (/client_secret|confidential/i.test(auth) && !/public clients: none/i.test(auth)) {
    parts.push(`-u "${clientId}:{client_secret}"`);
  }

  const example = ep.requestExample?.trim();
  if (example && ep.contentType === "application/json") {
    parts.push('-H "Content-Type: application/json"');
    parts.push(`--data '${example.replace(/"••••••••"/g, '"{password}"').replace(/'/g, "'\\''")}'`);
  } else if (example && ep.contentType === "application/x-www-form-urlencoded") {
    example
      .split("\n")
      .filter((line) => !line.trim().startsWith("#"))
      .join("")
      .split("&")
      .map((pair) => pair.trim())
      .filter(Boolean)
      .forEach((pair) => parts.push(`--data-urlencode "${pair}"`));
  }

  lines.push(parts.join(" \\\n  "));
  return lines.join("\n");
}

const QUERY_EXAMPLES: Record<string, string> = {
  response_type: "code",
  code_challenge_method: "S256",
  scope: "openid%20profile%20email",
};
