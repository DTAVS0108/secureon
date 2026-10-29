import { NextResponse } from "next/server";
import { getSpaces } from "@/lib/documentation/catalog";
import { getDocsViewer } from "@/lib/documentation/viewer";

/** The spaces and page tree the reader may see (pages for operators are listed with locked: true when signed out). */
export async function GET() {
  const viewer = await getDocsViewer();
  return NextResponse.json({
    name: "SecureOne Documentation",
    uiPath: "/documentation",
    contentApiPattern: "/api/documentation/{slug}",
    searchApiPath: "/api/documentation/search",
    spaces: await getSpaces(viewer),
  });
}
