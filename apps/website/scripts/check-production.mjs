/**
 * The website is for people running SecureOne in production. After `astro build`, this fails the build when a page
 * shows development content: development mode, the trial quickstart, local addresses, developer tools, source builds
 * or roadmap notes. Put such passages in <!-- not on website --> blocks (or `website: false`) in docs/. It also checks
 * the search-result basics of every page: exactly one <h1>, and a description of 50 to 165 characters.
 *
 *   node scripts/check-production.mjs [dist folder]
 */
import fs from "node:fs";
import path from "node:path";

const dist = path.resolve(process.argv[2] || "dist");
const RULES = [
  ["development mode", /SECUREONE_ENV\s*[=:]\s*development|\bdevelopment mode\b|\bin (?:local )?development\b|\bfor development\b|\bdev server\b|\blocal dev\b/i],
  ["trial quickstart", /\bquick ?start\b|\bon your laptop\b|\bfive minutes\b|\btrial only\b|\bnot for production\b/i],
  ["emergency defaults", /\badmin\s*\/\s*admin\b/i],
  ["local address", /\blocalhost\b|\b127\.0\.0\.1\b/i],
  ["developer tools", /\bMailHog\b|\bMailpit\b|\bgradlew\b|\bnpm run dev\b|\bpnpm (?:install|dev)\b|\bgit clone\b/i],
  ["roadmap", /\bPhase \d\b|\bMVP\b|\bin later phases\b/],
];
// Production practice, not development: the port bound to the host for the HTTPS proxy, and health checks run
// inside the container.
const ALLOWED = [
  /127\.0\.0\.1:9000:9000/g,
  /http:\/\/127\.0\.0\.1:9000\b/g,
  /http:\/\/127\.0\.0\.1:9001\/actuator\/health/g,
];

function mainText(html) {
  const main = /<main\b[\s\S]*?<\/main>/.exec(html);
  return (main ? main[0] : html)
    .replace(/<(script|style|nav|header|footer)\b[\s\S]*?<\/\1>/g, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&")
    .replace(/\s+/g, " ");
}

function pages(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? pages(p) : e.name.endsWith(".html") ? [p] : [];
  });
}

// Search-result basics: one main heading, and a description search engines show whole (the 404 page is not indexed).
function pageBasics(html, url) {
  const found = [];
  const h1 = (html.match(/<h1\b/g) || []).length;
  if (h1 !== 1) found.push(`${url}  [headings]  ${h1} <h1> elements (one per page)`);
  const desc = /<meta name="description" content="([^"]*)"/.exec(html);
  const length = desc ? desc[1].replace(/&[a-z#0-9]+;/g, "x").length : 0;
  if (url !== "/404.html" && (length < 50 || length > 165)) {
    found.push(`${url}  [description]  ${length} characters (50 to 165: front-matter summary or the page's description)`);
  }
  return found;
}

const problems = [];
for (const file of pages(dist)) {
  const html = fs.readFileSync(file, "utf8");
  let text = mainText(html);
  for (const allowed of ALLOWED) text = text.replace(allowed, " ");
  const url = "/" + path.relative(dist, file).split(path.sep).join("/").replace(/index\.html$/, "");
  if (!url.startsWith("/pagefind/")) problems.push(...pageBasics(html, url));
  for (const [name, rule] of RULES) {
    const m = rule.exec(text);
    if (m) problems.push(`${url}  [${name}]  …${text.slice(Math.max(0, m.index - 60), m.index + m[0].length + 60).trim()}…`);
  }
}
if (problems.length) {
  console.error(`check-production: ${problems.length} problem(s) (development content or page basics):\n` + problems.join("\n"));
  process.exit(1);
}
console.log(`check-production: ${pages(dist).length} pages, no development content, page basics fine`);
