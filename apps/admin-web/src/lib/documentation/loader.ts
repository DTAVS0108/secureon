import "server-only";

import fs from "fs/promises";
import path from "path";
import { drawioDocumentSlug, findNode, slugForFile, splitFrontMatter } from "./catalog";
import { docsRoot } from "./assets";
import { createSlugger, plainText, type DocsHeading, type DocsNode } from "./types";

export async function docsRootExists(): Promise<boolean> {
  try {
    await fs.access(docsRoot());
    return true;
  } catch {
    return false;
  }
}

function resolveDocsRelativeAssetPath(pageDir: string, href: string): string | null {
  const [filePart] = href.split("#");
  const assetPath = path.posix.normalize(path.posix.join(pageDir, filePart)).replace(/^\.\//, "");
  if (assetPath.startsWith("..") || path.isAbsolute(assetPath)) return null;
  return assetPath;
}

/** `drawio-hidden` renders collapsed: used when the page also draws the same diagram in Mermaid. */
function drawioFence(assetPath: string, hidden: boolean): string {
  const language = hidden ? "drawio-hidden" : "drawio";
  return `\n\n\`\`\`${language}\n${assetPath}\n\`\`\`\n`;
}

/** True when a Mermaid block follows in the same section, before the next heading or draw.io link. */
function hasMermaidTwin(lines: string[], from: number): boolean {
  for (let i = from + 1; i < lines.length; i++) {
    const next = lines[i].trimStart();
    if (next.startsWith("```mermaid")) return true;
    if (next.startsWith("#") || /\.drawio/i.test(next)) return false;
  }
  return false;
}

/** Embed draw.io viewers inline in doc pages; index table links point at the page that shows the diagram. */
function processDrawioEmbeds(content: string, pageFile: string): string {
  const pageDir = path.posix.dirname(pageFile);
  const lines = content.split("\n");
  const out: string[] = [];
  for (const [index, line] of lines.entries()) {
    if (!/\.drawio/i.test(line)) {
      out.push(line);
      continue;
    }
    if (line.trimStart().startsWith("|")) {
      out.push(
        line.replace(/\[([^\]]+)\]\(([^)]+\.drawio)\)/gi, (match, text, href) => {
          const assetPath = resolveDocsRelativeAssetPath(pageDir, href);
          const slug = assetPath ? drawioDocumentSlug(assetPath) : undefined;
          return slug ? `[${text}](/documentation/${slug})` : match;
        }),
      );
      continue;
    }
    let embedPath: string | null = null;
    const rewritten = line.replace(/\[([^\]]+)\]\(([^)]+\.drawio)\)/gi, (match, text, href) => {
      const assetPath = resolveDocsRelativeAssetPath(pageDir, href);
      if (!assetPath) return match;
      embedPath = assetPath;
      return `[${text}](/api/documentation/assets/${assetPath})`;
    });
    out.push(rewritten);
    if (embedPath) out.push(drawioFence(embedPath, hasMermaidTwin(lines, index)));
  }
  return out.join("\n");
}

/** Relative links between pages become console links; images and other files go through the assets route. */
function rewriteLinks(content: string, pageFile: string): string {
  const pageDir = path.posix.dirname(pageFile);
  return content.replace(/(!?)\[([^\]]*)\]\(([^)\s]+)\)/g, (match, bang: string, text: string, href: string) => {
    if (/^(https?:|mailto:|#|\/|\$)/.test(href)) return match;
    const [filePart, anchor = ""] = href.split("#");
    const target = path.posix.normalize(path.posix.join(pageDir, filePart));
    if (target.startsWith("..")) return bang ? match : text; // outside docs/: no console page to open
    if (/\.md$/i.test(target)) {
      const slug = target === "README.md" ? "" : slugForFile(target);
      return `${bang}[${text}](/documentation${slug ? `/${slug}` : ""}${anchor ? `#${anchor}` : ""})`;
    }
    if (/\.drawio$/i.test(target)) return match;
    return `${bang}[${text}](/api/documentation/assets/${target})`;
  });
}

/** h2 and h3 headings outside code blocks, with the ids the renderer gives them. */
function headingsOf(content: string): DocsHeading[] {
  const slug = createSlugger();
  const out: DocsHeading[] = [];
  let inCode = false;
  for (const line of content.split("\n")) {
    if (/^\s*```/.test(line)) inCode = !inCode;
    if (inCode) continue;
    const m = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (!m) continue;
    const text = plainText(m[2]);
    const id = slug(text);
    if (m[1].length === 2 || m[1].length === 3) out.push({ depth: m[1].length as 2 | 3, text, id });
  }
  return out;
}

export type LoadedDoc = {
  node: DocsNode;
  /** Markdown to render: no front matter, no leading H1 (the page header shows the title). */
  content: string;
  headings: DocsHeading[];
};

export async function readBody(file: string): Promise<string> {
  const raw = await fs.readFile(path.join(/*turbopackIgnore: true*/ docsRoot(), file), "utf8");
  return splitFrontMatter(raw).body.replace(/\r\n/g, "\n").replace(/^\s*#\s+[^\n]*\n+/, "");
}

export async function loadDoc(slug: string, options?: { embedDrawio?: boolean }): Promise<LoadedDoc | null> {
  const node = await findNode(slug);
  if (!node) return null;
  try {
    const body = await readBody(node.file);
    const withDrawio = options?.embedDrawio === false ? body : processDrawioEmbeds(body, node.file);
    const content = rewriteLinks(withDrawio, node.file);
    return { node, content, headings: headingsOf(content) };
  } catch {
    return null;
  }
}
