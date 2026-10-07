import { ConfluenceSidebar } from "./ConfluenceSidebar";

export function ConfluenceShell({
  activeSlug,
  title,
  sourceFile,
  children,
}: {
  activeSlug: string;
  title: string;
  sourceFile: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-start justify-between gap-4 border-b border-ui bg-ui-surface px-8 py-5">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wider text-faint">SecureOne Confluence</p>
          <h1 className="mt-1 text-2xl font-semibold text-ui">{title}</h1>
          <p className="mt-1 font-mono text-xs text-muted">docs/{sourceFile}</p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <a
            href={`/confluence/print/${activeSlug}`}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-lg border border-ui px-3 py-2 text-sm font-medium text-soft transition-colors hover:bg-ui-elevated"
            title="Open a print-friendly view of this page and save it as PDF"
          >
            Download PDF
          </a>
          <a
            href="/confluence/print/all"
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-lg border border-ui px-3 py-2 text-sm font-medium text-soft transition-colors hover:bg-ui-elevated"
            title="Export all documentation pages as a single PDF"
          >
            All docs PDF
          </a>
        </div>
      </header>
      <div className="flex min-h-0 flex-1">
        <ConfluenceSidebar activeSlug={activeSlug} />
        <div className="min-w-0 flex-1 overflow-y-auto px-8 py-8">{children}</div>
      </div>
    </div>
  );
}
