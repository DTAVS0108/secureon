import { NextResponse } from "next/server";
import { searchIndex } from "@/lib/documentation/search";
import { getDocsViewer } from "@/lib/documentation/viewer";

/** Titles, headings and text of every page the reader may read; the search box matches them in the browser. */
export async function GET() {
  const entries = await searchIndex(await getDocsViewer());
  return NextResponse.json(entries, { headers: { "Cache-Control": "private, max-age=60" } });
}
