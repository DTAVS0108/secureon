import fs from "fs/promises";
import path from "path";
import {
  buildFileToSlugMap,
  drawioDocumentSlug,
  getConfluencePage,
  type ConfluencePage,
} from "./catalog";

function docsRoot(): string {
  return path.join(process.cwd(), "..", "..", "docs");
}

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

function drawioFence(assetPath: string): string {
  return `\n\n\`\`\`drawio\n${assetPath}\n\`\`\`\n`;
}

/** Embed draw.io viewers inline in doc pages; index table links point at parent docs. */
function processDrawioEmbeds(content: string, pageFile: string): string {
  const pageDir = path.posix.dirname(pageFile.replace(/\\/g, "/"));
  const lines = content.split("\n");
  const out: string[] = [];

  for (const line of lines) {
    const isTableRow = line.trimStart().startsWith("|");

    if (isTableRow && /\.drawio/i.test(line)) {
      const rewritten = line.replace(/\[([^\]]+)\]\(([^)]+\.drawio)\)/gi, (match, text, href) => {
        const assetPath = resolveDocsRelativeAssetPath(pageDir, href);
        if (!assetPath) return match;
        const slug = drawioDocumentSlug(assetPath);
        if (!slug) return match;
        return `[${text}](/confluence/${slug})`;
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
        return `[${text}](/api/confluence/assets/${assetPath})`;
      });

      out.push(rewritten);
      if (embedPath) out.push(drawioFence(embedPath));
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
      return `[${text}](/api/confluence/assets/${assetPath})`;
    }

    const slug =
      slugByFile.get(filePart) ??
      slugByFile.get(normalized) ??
      slugByFile.get(filePart.split("/").pop() ?? "");
    if (!slug) return match;
    const hash = anchor ? `#${anchor}` : "";
    return `[${text}](/confluence/${slug}${hash})`;
  });
}

export type LoadedConfluenceDoc = {
  page: ConfluencePage;
  content: string;
  format: "markdown";
};

export async function loadConfluenceDoc(
  slug: string,
  options?: { embedDrawio?: boolean },
): Promise<LoadedConfluenceDoc | null> {
  const page = getConfluencePage(slug);
  if (!page) return null;

  const embedDrawio = options?.embedDrawio ?? true;
  const filePath = path.join(docsRoot(), page.file);
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
