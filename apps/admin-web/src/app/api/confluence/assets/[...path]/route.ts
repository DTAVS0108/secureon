import fs from "fs/promises";
import { NextResponse } from "next/server";
import {
  contentTypeForAsset,
  resolveDocsAssetPath,
} from "@/lib/confluence/assets";
import { docsRootExists } from "@/lib/confluence/loader";

export async function GET(
  _request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  if (!(await docsRootExists())) {
    return NextResponse.json({ error: "Documentation root not available" }, { status: 503 });
  }

  const { path: segments } = await context.params;
  const filePath = resolveDocsAssetPath(segments);
  if (!filePath) {
    return NextResponse.json({ error: "Invalid path" }, { status: 400 });
  }

  try {
    const data = await fs.readFile(filePath);
    const name = segments[segments.length - 1] ?? "asset";
    return new NextResponse(data, {
      headers: {
        "Content-Type": contentTypeForAsset(filePath),
        "Content-Disposition": `inline; filename="${name}"`,
        "Cache-Control": "private, max-age=300",
      },
    });
  } catch {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }
}
