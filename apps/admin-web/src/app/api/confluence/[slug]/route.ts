import { NextResponse } from "next/server";
import { docsRootExists, loadConfluenceDoc } from "@/lib/confluence/loader";

export async function GET(
  _request: Request,
  context: { params: Promise<{ slug: string }> },
) {
  const { slug } = await context.params;

  if (!(await docsRootExists())) {
    return NextResponse.json({ error: "Documentation root not available" }, { status: 503 });
  }

  const doc = await loadConfluenceDoc(slug);
  if (!doc) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }

  return NextResponse.json({
    slug: doc.page.slug,
    title: doc.page.title,
    file: doc.page.file,
    description: doc.page.description ?? null,
    format: doc.format,
    content: doc.content,
    uiUrl: `/confluence/${slug}`,
  });
}
