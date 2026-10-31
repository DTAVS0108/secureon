import "server-only";

import { allNodes, trail } from "./catalog";
import { applyDeveloperBlocks, readBody } from "./loader";
import { canRead, plainText, type DocsViewer } from "./types";

export type SearchEntry = {
  slug: string;
  title: string;
  summary: string;
  /** Space › section, for context in results. */
  where: string;
  headings: string[];
  /** Body as plain text (shortened), for matching words that are not in titles. */
  text: string;
};

/** Everything the viewer may read, ready for the search box to match in the browser. */
export async function searchIndex(viewer: DocsViewer): Promise<SearchEntry[]> {
  const out: SearchEntry[] = [];
  for (const node of await allNodes()) {
    if (!canRead(node.audience, viewer)) continue;
    const body = applyDeveloperBlocks(await readBody(node.file).catch(() => ""), viewer.platformOperator);
    const headings: string[] = [];
    const text: string[] = [];
    let inCode = false;
    for (const line of body.split("\n")) {
      if (/^\s*```/.test(line)) {
        inCode = !inCode;
        continue;
      }
      const heading = /^#{2,4}\s+(.+)/.exec(line);
      if (heading && !inCode) headings.push(plainText(heading[1]));
      else if (!inCode) text.push(plainText(line.replace(/^[>|\-*\s]+/, "")));
    }
    const path = (await trail(node.slug)).slice(0, -1).map((n) => n.title);
    out.push({
      slug: node.slug,
      title: node.title,
      summary: node.summary,
      where: path.join(" › "),
      headings,
      text: text.join(" ").replace(/\s+/g, " ").slice(0, 6000),
    });
  }
  return out;
}
