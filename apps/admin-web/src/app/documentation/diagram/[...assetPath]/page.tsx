import { redirect } from "next/navigation";
import { drawioDocumentSlug } from "@/lib/documentation/catalog";
import { assetPathFromSegments, isDrawioAssetPath } from "@/lib/documentation/assets";

export const dynamic = "force-dynamic";

/** Legacy diagram URLs redirect to the parent documentation page with inline embeds. */
export default async function DocsDiagramRedirectPage({
  params,
}: {
  params: Promise<{ assetPath: string[] }>;
}) {
  const { assetPath: segments } = await params;
  const assetPath = assetPathFromSegments(segments);

  if (!isDrawioAssetPath(assetPath)) {
    redirect("/documentation/diagrams");
  }

  const slug = drawioDocumentSlug(assetPath) ?? "diagrams";
  redirect(`/documentation/${slug}`);
}
