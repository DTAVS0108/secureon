import { notFound } from "next/navigation";
import { DocsMarkdown } from "@/components/documentation/DocsMarkdown";
import { PdfPrintBar } from "@/components/documentation/PdfPrintBar";
import { allNodes, findNode, trail } from "@/lib/documentation/catalog";
import { docsRootExists, loadDoc, type LoadedDoc } from "@/lib/documentation/loader";
import { getDocsViewer } from "@/lib/documentation/viewer";
import { canRead } from "@/lib/documentation/types";

export const dynamic = "force-dynamic";

type PrintDoc = LoadedDoc & { where: string };

/** Print-friendly view: one page (print/<slug>) or every page the reader may see (print/all), then "Save as PDF". */
export default async function DocsPrintPage({ params }: { params: Promise<{ scope: string[] }> }) {
  const scope = (await params).scope.join("/");
  if (!(await docsRootExists())) notFound();
  const viewer = await getDocsViewer();

  const nodes =
    scope === "all"
      ? (await allNodes()).filter((n) => canRead(n.audience, viewer))
      : [await findNode(scope)].filter((n) => n !== undefined && canRead(n.audience, viewer));
  const docs: PrintDoc[] = [];
  for (const node of nodes) {
    // draw.io iframes cannot print; their Mermaid twins and links remain.
    const doc = await loadDoc(node!.slug, viewer, { embedDrawio: false });
    if (doc) docs.push({ ...doc, where: (await trail(node!.slug)).slice(0, -1).map((n) => n.title).join(" › ") });
  }
  if (docs.length === 0) notFound();

  const title = scope === "all" ? "SecureOne documentation" : docs[0].node.title;
  const generatedAt = new Date().toISOString().slice(0, 10);

  return (
    <div className="docs-print min-h-screen bg-ui-surface">
      <PdfPrintBar title={title} />
      <div className="mx-auto max-w-4xl px-8 py-8">
        <header className="mb-10 border-b border-ui pb-6">
          <p className="text-xs font-semibold uppercase tracking-wider text-faint">SecureOne Documentation</p>
          <h1 className="mt-1 text-3xl font-semibold text-ui">{title}</h1>
          <p className="mt-2 text-sm text-muted">
            Exported {generatedAt}
            {scope === "all" ? ` · ${docs.length} pages` : viewer.platformOperator ? ` · docs/${docs[0].node.file}` : ""}
          </p>
        </header>

        {scope === "all" && (
          <nav className="print-page-break-after mb-10">
            <h2 className="mb-3 text-xl font-semibold text-ui">Contents</h2>
            <ul className="space-y-1 text-sm text-soft">
              {docs.map((doc) => (
                <li key={doc.node.slug}>
                  {doc.node.title}
                  {doc.where && <span className="text-muted"> — {doc.where}</span>}
                </li>
              ))}
            </ul>
          </nav>
        )}

        {docs.map((doc, index) => (
          <section key={doc.node.slug} className={index > 0 ? "print-page-break-before mt-12" : undefined}>
            {scope === "all" && (
              <div className="mb-6 border-b-2 border-ui pb-3">
                {doc.where && <p className="text-xs font-semibold uppercase tracking-wider text-faint">{doc.where}</p>}
                <h2 className="mt-1 text-2xl font-semibold text-ui">{doc.node.title}</h2>
              </div>
            )}
            <DocsMarkdown content={doc.content} />
          </section>
        ))}
      </div>
    </div>
  );
}
