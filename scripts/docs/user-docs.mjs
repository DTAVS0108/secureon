/**
 * Copies the user documentation for the release image: every page except developer pages
 * (`audience: internal`), with developer blocks (<!-- developer --> ... <!-- /developer -->) removed.
 * Diagram sources (.drawio), the repository index (README.md) and other non-page files stay out.
 *
 *   node scripts/docs/user-docs.mjs <docs folder> <output folder>
 *
 * Used by deploy/Dockerfile; developer builds (--build-arg SECUREONE_DOCS=all) copy docs/ unchanged.
 */
import fs from "node:fs";
import path from "node:path";

const [source, target] = process.argv.slice(2);
if (!source || !target) {
  console.error("usage: node user-docs.mjs <docs folder> <output folder>");
  process.exit(2);
}
const FRONT = /^﻿?---\r?\n([\s\S]*?)\r?\n---\r?\n/;
const DEVELOPER_BLOCK = /<!--\s*developer\s*-->[\s\S]*?<!--\s*\/developer\s*-->[^\S\n]*\r?\n?/g;
// Images a user page may show; everything else (diagram sources, scripts) is for developers.
const ASSETS = new Set([".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"]);

function audience(raw) {
  const m = FRONT.exec(raw);
  const line = m && /^audience:\s*(\S+)/m.exec(m[1]);
  // Same rule as the console: missing or unknown means internal, so nothing ships by accident.
  return line && ["public", "operator", "internal"].includes(line[1]) ? line[1] : "internal";
}

let pages = 0;
let skipped = 0;
function copy(dir, rel = "") {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const from = path.join(dir, entry.name);
    const name = rel ? `${rel}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      copy(from, name);
      continue;
    }
    const ext = path.extname(entry.name).toLowerCase();
    const to = path.join(target, name);
    if (ext === ".md") {
      const raw = fs.readFileSync(from, "utf8");
      if (name === "README.md" || audience(raw) === "internal") {
        skipped++;
        continue;
      }
      fs.mkdirSync(path.dirname(to), { recursive: true });
      fs.writeFileSync(to, raw.replace(DEVELOPER_BLOCK, ""));
      pages++;
    } else if (ASSETS.has(ext)) {
      fs.mkdirSync(path.dirname(to), { recursive: true });
      fs.copyFileSync(from, to);
    }
  }
}

fs.rmSync(target, { recursive: true, force: true });
copy(path.resolve(source));
console.log(`user-docs: ${pages} pages copied, ${skipped} developer pages left out`);
