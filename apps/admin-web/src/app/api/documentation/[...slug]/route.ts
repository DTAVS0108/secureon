import { NextResponse } from "next/server";
import { docsRootExists, loadDoc } from "@/lib/documentation/loader";
import { findNode } from "@/lib/documentation/catalog";
import { getDocsViewer } from "@/lib/documentation/viewer";
import { canRead } from "@/lib/documentation/types";

/** One page as Markdown, if the reader may read it. */
export async function GET(_request: Request, context: { params: Promise<{ slug: string[] }> }) {
  const slug = (await context.params).slug.join("/");
  if (!(await docsRootExists())) {
    return NextResponse.json({ error: "Documentation root not available" }, { status: 503 });
  }
  const node = await findNode(slug);
  const viewer = await getDocsViewer();
  if (!node || (node.audience === "internal" && !viewer.platformOperator)) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }
  if (!canRead(node.audience, viewer)) {
    return NextResponse.json({ error: "Sign in to read this page" }, { status: 401 });
  }
  const doc = await loadDoc(slug, viewer);
  if (!doc) return NextResponse.json({ error: "Not found" }, { status: 404 });
  return NextResponse.json({
    slug,
    title: node.title,
    summary: node.summary,
    audience: node.audience,
    ...(viewer.platformOperator ? { file: node.file } : {}),
    format: "markdown",
    content: doc.content,
    headings: doc.headings,
    uiPath: `/documentation/${slug}`,
  });
}
