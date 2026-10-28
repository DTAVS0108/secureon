import fs from "fs/promises";
import path from "path";
import {
  buildFileToSlugMap,
  drawioDocumentSlug,
  getDocsPage,
  type DocsPage,
} from "./catalog";
import { docsRoot } from "./assets";


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
  const normalized = filePart.replace(/^\.\//, "").replace(/^\.\.\//, "");
  const assetPath = path.posix.normalize(path.posix.join(pageDir, normalized)).replace(/^\.\//, "");
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

/** Embed draw.io viewers inline in doc pages; index table links point at parent docs. */
function processDrawioEmbeds(content: string, pageFile: string): string {
  const pageDir = path.posix.dirname(pageFile.replace(/\\/g, "/"));
  const lines = content.split("\n");
  const out: string[] = [];

  for (const [index, line] of lines.entries()) {
    const isTableRow = line.trimStart().startsWith("|");

    if (isTableRow && /\.drawio/i.test(line)) {
      const rewritten = line.replace(/\[([^\]]+)\]\(([^)]+\.drawio)\)/gi, (match, text, href) => {
        const assetPath = resolveDocsRelativeAssetPath(pageDir, href);
        if (!assetPath) return match;
        const slug = drawioDocumentSlug(assetPath);
        if (!slug) return match;
        return `[${text}](/documentation/${slug})`;
      });
      out.push(rewritten);
      continue;
    }

    if (/\.drawio/i.test(line)) {
      let rewritten = line;
      let embedPath: string | null = null;

      rewritten = rewritten.replace(/\[([^\]]+)\]\(([^)]+\.drawio)\)/gi, (match, text, href) => {
        const assetPath = resolveDocsRelativeAssetPath(pageDir, href);
        if (!assetPath) return match;
        embedPath = assetPath;
        return `[${text}](/api/documentation/assets/${assetPath})`;
      });

      out.push(rewritten);
      if (embedPath) out.push(drawioFence(embedPath, hasMermaidTwin(lines, index)));
      continue;
    }

    out.push(line);
  }

  return out.join("\n");
}

function rewriteMarkdownLinks(content: string, pageFile: string): string {
  const slugByFile = buildFileToSlugMap();
  const pageDir = path.posix.dirname(pageFile.replace(/\\/g, "/"));

  return content.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, text, href) => {
    if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("/")) {
      return match;
    }
    const [filePart, anchor = ""] = href.split("#");
    const normalized = filePart.replace(/^\.\//, "").replace(/^\.\.\//, "");

    if (/\.drawio$/i.test(filePart)) {
      return match;
    }

    if (/\.(png|svg|jpg|jpeg|gif|webp)$/i.test(filePart)) {
      const assetPath = path.posix
        .normalize(path.posix.join(pageDir, normalized))
        .replace(/^\.\//, "");
      if (assetPath.startsWith("..")) return match;
      return `[${text}](/api/documentation/assets/${assetPath})`;
    }

    // Resolve relative to the page's folder first, so `README.md` or `../x.md` inside a subfolder
    // (docs/user-guide/) finds the right page before the bare file-name fallbacks.
    const resolved = path.posix.normalize(path.posix.join(pageDir, filePart));
    const slug =
      slugByFile.get(resolved) ??
      slugByFile.get(filePart) ??
      slugByFile.get(normalized) ??
      slugByFile.get(filePart.split("/").pop() ?? "");
    if (!slug) return match;
    const hash = anchor ? `#${anchor}` : "";
    return `[${text}](/documentation/${slug}${hash})`;
  });
}

export type LoadedDoc = {
  page: DocsPage;
  content: string;
  format: "markdown";
};

export async function loadDoc(
  slug: string,
  options?: { embedDrawio?: boolean },
): Promise<LoadedDoc | null> {
  const page = getDocsPage(slug);
  if (!page) return null;

  const embedDrawio = options?.embedDrawio ?? true;
  const filePath = path.join(/*turbopackIgnore: true*/ docsRoot(), page.file);
  try {
    const raw = await fs.readFile(filePath, "utf8");
    const withDrawio = embedDrawio ? processDrawioEmbeds(raw, page.file) : raw;
    return {
      page,
      content: rewriteMarkdownLinks(withDrawio, page.file),
      format: "markdown",
    };
  } catch {
    return null;
  }
}
