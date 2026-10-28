import { notFound } from "next/navigation";
import { DocsMarkdown } from "@/components/documentation/DocsMarkdown";
import { DocsShell } from "@/components/documentation/DocsShell";
import { docsRootExists, loadDoc } from "@/lib/documentation/loader";

export const dynamic = "force-dynamic";

export default async function DocPage({
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

  const doc = await loadDoc(slug);
  if (!doc) notFound();

  return (
    <DocsShell activeSlug={slug} title={doc.page.title} sourceFile={doc.page.file}>
      <DocsMarkdown content={doc.content} />
    </DocsShell>
  );
}
