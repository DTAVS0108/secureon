/**
 * Copies the user documentation from the repository's docs/ into the website (src/content/docs/docs/).
 *
 * docs/ is the single source. The website is the complete guide for users: it shows every page except developer
 * pages (`audience: internal`), without their developer blocks (<!-- developer --> ... <!-- /developer -->). Links to
 * developer pages and to source files become plain text, so nothing internal leaks.
 * Run by `pnpm build` and `pnpm dev`; never edit the generated folder.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const DOCS = process.env.SECUREONE_DOCS_DIR || path.resolve(here, "../../../docs");
const OUT = path.resolve(here, "../src/content/docs/docs");
// Developer-only parts of a page (local setup, source builds) never reach the website.
const DEVELOPER_BLOCK = /<!--\s*developer\s*-->[\s\S]*?<!--\s*\/developer\s*-->[^\S\n]*\n?/g;

const FRONT = /^﻿?---\r?\n([\s\S]*?)\r?\n---\r?\n/;
const HTML_OK = new Set(["br", "details", "summary", "sup", "sub", "kbd", "img", "a", "p", "div", "span", "b", "i", "em", "strong", "code", "pre", "table", "thead", "tbody", "tr", "td", "th", "ul", "ol", "li", "hr"]);
const ASIDE = { note: "note", tip: "tip", important: "note", warning: "caution", caution: "danger" };

function frontMatter(raw) {
  const m = FRONT.exec(raw);
  const fields = {};
  if (m) {
    for (const line of m[1].split(/\r?\n/)) {
      const pair = /^([A-Za-z][\w-]*):\s*(.*?)\s*$/.exec(line);
      if (pair) fields[pair[1]] = pair[2];
    }
  }
  return { fields, body: m ? raw.slice(m[0].length) : raw };
}

function walk(dir, base = "") {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const rel = base ? `${base}/${e.name}` : e.name;
    if (e.isDirectory()) return walk(path.join(dir, e.name), rel);
    return e.name.endsWith(".md") ? [rel] : [];
  });
}

const pages = new Map();
for (const rel of walk(DOCS)) {
  if (rel === "README.md") continue;
  const raw = fs.readFileSync(path.join(DOCS, rel), "utf8").replace(/\r\n/g, "\n");
  const { fields, body } = frontMatter(raw);
  pages.set(rel, { rel, fields, body });
}
// Public and operator pages (operator guides and runbooks) are for users; internal pages are for developers.
const onSite = (rel) => pages.has(rel) && pages.get(rel).fields.audience !== "internal";

/** /docs/<slug>/ for a docs file: folder index.md is the folder. */
function route(rel) {
  const slug = rel.replace(/\.md$/, "").replace(/(^|\/)index$/, "");
  return `/docs/${slug}${slug ? "/" : ""}`;
}

function quote(value) {
  return JSON.stringify(value ?? "");
}

/** Outside code: escape <placeholders> (Markdown would treat them as HTML tags and hide them). */
function escapeAngles(line) {
  return line
    .split(/(`[^`]*`)/)
    .map((part, i) =>
      i % 2 === 1 ? part : part.replace(/<(\/?)([A-Za-z][\w.:-]*)/g, (m, slash, tag) => (HTML_OK.has(tag.toLowerCase()) ? m : `&lt;${slash}${tag}`)),
    )
    .join("");
}

function convert(page) {
  const dir = path.posix.dirname(page.rel);
  const lines = page.body.replace(DEVELOPER_BLOCK, "").replace(/^\s*#\s+[^\n]*\n+/, "").split("\n");
  const out = [];
  let fence = null; // "code" | "mermaid"
  let aside = null;
  for (let line of lines) {
    const fenceMatch = /^\s*```(\S*)/.exec(line);
    if (fenceMatch && !fence) {
      fence = fenceMatch[1] === "mermaid" ? "mermaid" : "code";
      out.push(fence === "mermaid" ? '<pre class="mermaid not-content">' : line);
      continue;
    }
    if (fenceMatch && fence) {
      out.push(fence === "mermaid" ? "</pre>" : line);
      fence = null;
      continue;
    }
    if (fence === "mermaid") {
      out.push(line.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"));
      continue;
    }
    if (fence) {
      out.push(line);
      continue;
    }
    // GitHub alerts (> [!NOTE] …) -> Starlight asides (:::note … :::)
    const alert = /^>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$/i.exec(line);
    if (alert) {
      aside = ASIDE[alert[1].toLowerCase()];
      out.push(`:::${aside}`);
      continue;
    }
    if (aside) {
      if (/^>/.test(line)) {
        line = line.replace(/^>\s?/, "");
      } else {
        out.push(":::");
        aside = null;
      }
    }
    line = line.replace(/(!?)\[([^\]]*)\]\(([^)\s]+)\)/g, (m, bang, text, href) => {
      if (/^(https?:|mailto:|#)/.test(href)) return m;
      if (href.startsWith("/")) return bang ? "" : `\`${text.replace(/`/g, "")}\``; // a SecureOne server path, not this site
      if (href.startsWith("$")) return m;
      const [file, anchor] = href.split("#");
      const target = path.posix.normalize(path.posix.join(dir, file));
      if (target.startsWith("..")) return bang ? "" : text; // a source file: never linked from the website
      if (target.endsWith(".md")) {
        if (target === "README.md") return `${bang}[${text}](/docs/)`;
        if (!onSite(target)) return text; // developer page: not on the website
        return `${bang}[${text}](${route(target)}${anchor ? `#${anchor}` : ""})`;
      }
      return bang ? "" : text; // diagram sources and other files: not published
    });
    out.push(escapeAngles(line));
  }
  if (aside) out.push(":::");
  return out.join("\n");
}

fs.rmSync(OUT, { recursive: true, force: true });
let count = 0;
for (const page of pages.values()) {
  if (!onSite(page.rel)) continue;
  const { title, summary, order } = page.fields;
  const fm = [
    "---",
    `title: ${quote(title)}`,
    summary ? `description: ${quote(summary)}` : null,
    "sidebar:",
    // A space or section's own page comes first in its group, as "Overview".
    `  order: ${page.rel.endsWith("index.md") ? 0 : Number.parseInt(order, 10) || 999}`,
    page.rel.endsWith("index.md") ? `  label: "Overview"` : null,
    "---",
    "",
  ].filter((l) => l !== null);
  const target = path.join(OUT, page.rel);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, fm.join("\n") + convert(page).trim() + "\n");
  count++;
}

// Docs home: the spaces, from their index pages.
const spaces = [...pages.values()]
  .filter((p) => /^[^/]+\/index\.md$/.test(p.rel) && onSite(p.rel))
  .sort((a, b) => (Number(a.fields.order) || 999) - (Number(b.fields.order) || 999));
const home = [
  "---",
  'title: "SecureOne documentation"',
  'description: "Install SecureOne, run it, connect your applications and find every API."',
  "template: splash",
  "hero:",
  '  title: "SecureOne documentation"',
  '  tagline: "Install it, set it up, add sign-in to your applications and find every API."',
  "  actions:",
  '    - text: "Quickstart"',
  '      link: "/docs/get-started/quickstart/"',
  "      icon: right-arrow",
  '    - text: "Installation"',
  '      link: "/docs/install/installation/"',
  "      variant: secondary",
  '    - text: "Integration guide"',
  '      link: "/docs/integrate/integration-guide/"',
  "      variant: minimal",
  "---",
  "",
  'import { Card, CardGrid, LinkCard } from "@astrojs/starlight/components";',
  "",
  "<CardGrid>",
  ...spaces.map(
    (s) => `  <LinkCard title=${quote(s.fields.title)} description=${quote(s.fields.summary)} href=${quote(route(s.rel))} />`,
  ),
  "</CardGrid>",
  "",
];
fs.writeFileSync(path.join(OUT, "index.mdx"), home.join("\n"));
console.log(`sync-docs: ${count} pages from ${DOCS}`);
