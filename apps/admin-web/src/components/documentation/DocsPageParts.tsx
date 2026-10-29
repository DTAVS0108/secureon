import Link from "next/link";
import { withBasePath } from "@/lib/config";
import { AUDIENCE_LABEL, type DocsNode } from "@/lib/documentation/types";
import { ArrowLeftIcon, ArrowRightIcon, ChevronIcon, LockIcon, PrintIcon, SpaceIcon } from "./DocsIcons";

export function Breadcrumbs({ trail }: { trail: DocsNode[] }) {
  return (
    <nav aria-label="Breadcrumb" className="mb-4 flex flex-wrap items-center gap-1 text-[13px] text-muted">
      <Link href="/documentation" className="hover:text-brand">
        Documentation
      </Link>
      {trail.slice(0, -1).map((node) => (
        <span key={node.slug} className="flex items-center gap-1">
          <ChevronIcon className="h-3 w-3 text-faint" />
          <Link href={`/documentation/${node.slug}`} className="hover:text-brand">
            {node.title}
          </Link>
        </span>
      ))}
    </nav>
  );
}

export function AudienceBadge({ node }: { node: DocsNode }) {
  const label = AUDIENCE_LABEL[node.audience];
  if (!label) return null;
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-ui bg-ui-elevated px-2.5 py-0.5 text-[11px] font-medium text-soft">
      <LockIcon className="h-3 w-3" />
      {label}
    </span>
  );
}

export function PageHeader({ node, space }: { node: DocsNode; space?: DocsNode }) {
  return (
    <header className="mb-8 border-b border-ui pb-6">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        {space && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-muted px-2.5 py-0.5 text-[11px] font-medium text-brand">
            <SpaceIcon slug={space.slug} className="h-3.5 w-3.5" />
            {space.title}
          </span>
        )}
        <AudienceBadge node={node} />
      </div>
      <h1 className="text-3xl font-semibold tracking-tight text-ui sm:text-[2.1rem]">{node.title}</h1>
      {node.summary && <p className="mt-3 max-w-3xl text-[15px] leading-relaxed text-muted">{node.summary}</p>}
    </header>
  );
}

export function ChildCards({ nodes, heading = "In this section" }: { nodes: DocsNode[]; heading?: string }) {
  if (nodes.length === 0) return null;
  return (
    <section className="mt-12">
      <h2 className="mb-4 text-lg font-semibold text-ui">{heading}</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        {nodes.map((node) => (
          <Link
            key={node.slug}
            href={`/documentation/${node.slug}`}
            className="group rounded-xl border border-ui bg-ui-surface p-4 transition-all hover:-translate-y-0.5 hover:border-[var(--ui-primary)] hover:shadow-md"
          >
            <span className="flex items-center gap-2 text-[15px] font-medium text-ui group-hover:text-brand">
              {node.title}
              {node.locked && <LockIcon className="h-3.5 w-3.5 text-faint" />}
              {node.children.length > 0 && (
                <span className="ml-auto rounded-full bg-ui-elevated px-2 text-[11px] font-normal text-muted">
                  {node.children.length} pages
                </span>
              )}
            </span>
            {node.summary && <span className="mt-1 block text-[13px] leading-snug text-muted">{node.summary}</span>}
          </Link>
        ))}
      </div>
    </section>
  );
}

export function PrevNext({ prev, next }: { prev?: DocsNode; next?: DocsNode }) {
  if (!prev && !next) return null;
  return (
    <nav aria-label="Previous and next page" className="mt-14 grid gap-3 border-t border-ui pt-6 sm:grid-cols-2">
      {prev ? (
        <Link href={`/documentation/${prev.slug}`} className="group rounded-xl border border-ui p-4 hover:border-[var(--ui-primary)]">
          <span className="flex items-center gap-1 text-[11px] text-faint">
            <ArrowLeftIcon className="h-3.5 w-3.5" /> Previous
          </span>
          <span className="mt-1 block text-sm font-medium text-ui group-hover:text-brand">{prev.title}</span>
        </Link>
      ) : (
        <span />
      )}
      {next && (
        <Link
          href={`/documentation/${next.slug}`}
          className="group rounded-xl border border-ui p-4 text-right hover:border-[var(--ui-primary)] sm:col-start-2"
        >
          <span className="flex items-center justify-end gap-1 text-[11px] text-faint">
            Next <ArrowRightIcon className="h-3.5 w-3.5" />
          </span>
          <span className="mt-1 block text-sm font-medium text-ui group-hover:text-brand">{next.title}</span>
        </Link>
      )}
    </nav>
  );
}

export function LockedNotice({ node, slug }: { node: DocsNode; slug: string }) {
  const operators = node.audience === "operator";
  return (
    <div className="rounded-2xl border border-ui bg-ui-elevated p-8 text-center">
      <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-brand-muted text-brand">
        <LockIcon className="h-6 w-6" />
      </span>
      <h2 className="text-lg font-semibold text-ui">{operators ? "Sign in to read this page" : "For SecureOne developers"}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-muted">
        {operators
          ? "This page is for people who run SecureOne. Sign in to the console to read it."
          : "This page describes SecureOne's internals and is shown to platform operators only."}
      </p>
      {operators && (
        <a
          href={withBasePath(`/login?next=${encodeURIComponent(`/documentation/${slug}`)}`)}
          className="mt-5 inline-flex rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand hover:opacity-90"
        >
          Sign in
        </a>
      )}
    </div>
  );
}

export function PageFooter({ node }: { node: DocsNode }) {
  return (
    <footer className="mt-10 flex flex-wrap items-center justify-between gap-3 text-xs text-faint">
      <span className="font-mono">docs/{node.file}</span>
      <a
        href={withBasePath(`/documentation/print/${node.slug}`)}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-flex items-center gap-1.5 rounded-lg border border-ui px-2.5 py-1.5 text-soft hover:bg-ui-elevated"
      >
        <PrintIcon className="h-3.5 w-3.5" /> Print or save as PDF
      </a>
    </footer>
  );
}
