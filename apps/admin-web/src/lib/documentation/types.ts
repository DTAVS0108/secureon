/** Shared (client-safe) documentation types. The catalog itself is built on the server from docs/ front matter. */

/** Who a page is for: everyone, signed-in console operators, or people working on SecureOne (platform operators). */
export type DocAudience = "public" | "operator" | "internal";

export type DocsNode = {
  /** URL path under /documentation: "install/deployment"; a folder's index.md is the folder ("install"). */
  slug: string;
  title: string;
  summary: string;
  order: number;
  audience: DocAudience;
  /** Path relative to docs/. */
  file: string;
  /** space: top-level folder; section: a folder inside a space; page: a single file. */
  kind: "space" | "section" | "page";
  children: DocsNode[];
  /** The viewer may not open it (shown with a lock so people know it exists). */
  locked?: boolean;
};

export type DocsHeading = { depth: 2 | 3; text: string; id: string };

export type DocsViewer = { signedIn: boolean; platformOperator: boolean };

export const AUDIENCE_LABEL: Record<DocAudience, string | null> = {
  public: null,
  operator: "Operators",
  internal: "Developers",
};

export function canRead(audience: DocAudience, viewer: DocsViewer): boolean {
  if (audience === "public") return true;
  if (audience === "operator") return viewer.signedIn;
  return viewer.platformOperator;
}

/** Heading ids the way GitHub makes them, so links like page.md#all-settings keep working. */
export function createSlugger() {
  const seen = new Map<string, number>();
  return (text: string): string => {
    const base = text
      .toLowerCase()
      .trim()
      .replace(/[^\p{L}\p{N}\s_-]/gu, "")
      .replace(/\s/g, "-");
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  };
}

/** Plain text of inline Markdown (for headings and search). */
export function plainText(markdown: string): string {
  return markdown
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[`*_~]/g, "")
    .replace(/<[^>]+>/g, "")
    .trim();
}
