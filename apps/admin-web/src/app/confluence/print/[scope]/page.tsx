import { notFound } from "next/navigation";
import { ConfluenceMarkdown } from "@/components/confluence/ConfluenceMarkdown";
import { PdfPrintBar } from "@/components/confluence/PdfPrintBar";
import { requireConfluenceAccess } from "@/lib/confluence/access";
import { CONFLUENCE_CATEGORIES } from "@/lib/confluence/catalog";
import { docsRootExists, loadConfluenceDoc, type LoadedConfluenceDoc } from "@/lib/confluence/loader";

export const dynamic = "force-dynamic";

type PrintDoc = LoadedConfluenceDoc & { categoryLabel?: string };

/** All catalog pages in sidebar order, deduplicated (some pages appear in two categories). */
async function loadAllDocs(): Promise<PrintDoc[]> {
  const seen = new Set<string>();
  const docs: PrintDoc[] = [];
  for (const category of CONFLUENCE_CATEGORIES) {
    for (const page of category.pages) {
      if (seen.has(page.slug)) continue;
      seen.add(page.slug);
      // Skip draw.io iframes in print output — they cannot render on paper.
      const doc = await loadConfluenceDoc(page.slug, { embedDrawio: false });
      if (doc) docs.push({ ...doc, categoryLabel: category.label });
    }
  }
  return docs;
}

export default async function ConfluencePrintPage({
  params,
}: {
  params: Promise<{ scope: string }>;
}) {
  await requireConfluenceAccess();
  const { scope } = await params;

  if (!(await docsRootExists())) notFound();

  let docs: PrintDoc[];
  let title: string;

  if (scope === "all") {
    docs = await loadAllDocs();
    if (docs.length === 0) notFound();
    title = "SecureOne documentation — complete export";
  } else {
    const doc = await loadConfluenceDoc(scope, { embedDrawio: false });
    if (!doc) notFound();
    docs = [doc];
    title = doc.page.title;
  }

  const generatedAt = new Date().toISOString().slice(0, 10);

  return (
    <div className="confluence-print min-h-screen bg-ui-surface">
      <PdfPrintBar title={title} />

      <div className="mx-auto max-w-4xl px-8 py-8">
        <header className="mb-10 border-b border-ui pb-6">
          <p className="text-xs font-semibold uppercase tracking-wider text-faint">SecureOne Confluence</p>
          <h1 className="mt-1 text-3xl font-semibold text-ui">{title}</h1>
          <p className="mt-2 text-sm text-muted">
            Exported {generatedAt}
            {scope === "all" ? ` · ${docs.length} documents` : ` · docs/${docs[0].page.file}`}
          </p>
        </header>

        {scope === "all" && (
          <nav className="print-page-break-after mb-10">
            <h2 className="mb-3 text-xl font-semibold text-ui">Contents</h2>
            <ol className="list-decimal space-y-1 pl-6 text-sm text-soft">
              {docs.map((doc) => (
                <li key={doc.page.slug}>
                  {doc.page.title}
                  {doc.categoryLabel && <span className="text-muted"> — {doc.categoryLabel}</span>}
                </li>
              ))}
            </ol>
          </nav>
        )}

        {docs.map((doc, index) => (
          <section key={doc.page.slug} className={index > 0 ? "print-page-break-before mt-12" : undefined}>
            {scope === "all" && (
              <div className="mb-6 border-b-2 border-ui pb-3">
                {doc.categoryLabel && (
                  <p className="text-xs font-semibold uppercase tracking-wider text-faint">{doc.categoryLabel}</p>
                )}
                <h2 className="mt-1 text-2xl font-semibold text-ui">
                  {index + 1}. {doc.page.title}
                </h2>
                <p className="mt-1 font-mono text-xs text-muted">docs/{doc.page.file}</p>
              </div>
            )}
            <ConfluenceMarkdown content={doc.content} />
          </section>
        ))}
      </div>
    </div>
  );
}
