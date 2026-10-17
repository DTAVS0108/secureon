import { setupFor, type SnippetValues } from "./snippets";

export type SetupFileInput = {
  appName: string;
  kindTitle: string;
  frameworkId: string;
  frameworkLabel: string;
  values: SnippetValues;
  /** Only included when the operator ticks "Include the client secret". */
  clientSecret?: string | null;
  confidential: boolean;
  grantTypes: string[];
  showRedirects: boolean;
  createdAt: Date;
};

const fence = (language: string, code: string) => "```" + language + "\n" + code.trimEnd() + "\n```";

/** A Markdown reference of a connected app: values, endpoints and the framework setup code. */
export function buildSetupMarkdown(input: SetupFileInput): string {
  const v = input.values;
  const rows: [string, string][] = [
    ["Application", input.appName],
    ["App type", input.kindTitle],
    ["Framework", input.frameworkLabel],
    ["Client ID", v.clientId],
    ["Client type", input.confidential ? "Confidential (has a secret)" : "Public (PKCE, no secret)"],
    ["Grant types", input.grantTypes.join(", ") || "—"],
    ["Scopes", v.scopes.join(" ") || "—"],
    ["Issuer (authority)", v.issuer],
    ["Discovery", `${v.issuer}/.well-known/openid-configuration`],
  ];
  if (input.showRedirects) {
    rows.push(["Redirect URL", v.redirectUri], ["After sign-out URL", v.postLogoutUri]);
  }

  const out: string[] = [
    `# ${input.appName} — SecureOne setup`,
    "",
    `Generated ${input.createdAt.toISOString()} by the SecureOne console (Connect an app).`,
    "",
    "| Setting | Value |",
    "|---------|-------|",
    ...rows.map(([k, val]) => `| ${k} | \`${val}\` |`),
    "",
  ];

  if (input.confidential) {
    out.push("## Client secret", "");
    if (input.clientSecret) {
      out.push(
        "> **Keep this file secret.** It contains the client secret. Store the value in your server's",
        "> secret store as `SECUREONE_CLIENT_SECRET`, then delete this file or keep it in a password manager.",
        "",
        fence("", input.clientSecret),
        "",
      );
    } else {
      out.push(
        "Not included. SecureOne showed it once when the client was created; if it is lost, rotate it on the",
        "client page (OAuth clients → the client → Rotate client secret).",
        "",
      );
    }
  }

  let setup: ReturnType<typeof setupFor> | null = null;
  try {
    setup = setupFor(input.frameworkId, v);
  } catch {
    setup = null;
  }
  if (setup) {
    out.push(`## Setup code (${input.frameworkLabel})`, "");
    if (setup.install) out.push("### Install", "", fence("bash", setup.install), "");
    for (const s of setup.snippets) out.push(`### ${s.title}`, "", fence(s.language, s.code), "");
    if (setup.notes.length > 0) out.push("### Notes", "", ...setup.notes.map((n) => `- ${n}`), "");
  }
  return out.join("\n");
}

/** Saves text as a file in the browser. */
export function downloadTextFile(fileName: string, text: string, type = "text/markdown") {
  const url = URL.createObjectURL(new Blob([text], { type: `${type};charset=utf-8` }));
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
