import { notFound } from "next/navigation";
import { ConfluenceMarkdown } from "@/components/confluence/ConfluenceMarkdown";
import { ConfluenceShell } from "@/components/confluence/ConfluenceShell";
import { docsRootExists, loadConfluenceDoc } from "@/lib/confluence/loader";

export const dynamic = "force-dynamic";

export default async function ConfluenceDocPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  if (!(await docsRootExists())) {
    return (
      <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-6 text-sm">
        Documentation folder not found. Run admin-web from the monorepo so <code>docs/</code> is available at the
        repository root.
      </div>
    );
  }

  const doc = await loadConfluenceDoc(slug);
  if (!doc) notFound();

  return (
    <ConfluenceShell activeSlug={slug} title={doc.page.title} sourceFile={doc.page.file}>
      <ConfluenceMarkdown content={doc.content} />
    </ConfluenceShell>
  );
}
