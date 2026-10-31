import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocsMarkdown } from "@/components/documentation/DocsMarkdown";
import { DocsShell } from "@/components/documentation/DocsShell";
import {
  Breadcrumbs,
  ChildCards,
  LockedNotice,
  PageFooter,
  PageHeader,
  PrevNext,
} from "@/components/documentation/DocsPageParts";
import { findNode, flatten, getSpaces, trail } from "@/lib/documentation/catalog";
import { docsRootExists, loadDoc } from "@/lib/documentation/loader";
import { getDocsViewer } from "@/lib/documentation/viewer";
import { canRead } from "@/lib/documentation/types";

export const dynamic = "force-dynamic";

type Params = { params: Promise<{ slug: string[] }> };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const node = await findNode((await params).slug.map(decodeURIComponent).join("/"));
  // Developer pages do not exist for other readers, not even their title.
  if (!node || (node.audience === "internal" && !(await getDocsViewer()).platformOperator)) return {};
  return { title: `${node.title} · SecureOne Documentation`, description: node.summary };
}

export default async function DocPage({ params }: Params) {
  const slug = (await params).slug.map(decodeURIComponent).join("/");
  if (!(await docsRootExists())) notFound();

  const viewer = await getDocsViewer();
  const node = await findNode(slug);
  // Developer pages do not exist for readers who may not see them.
  if (!node || (node.audience === "internal" && !viewer.platformOperator)) notFound();

  const spaces = await getSpaces(viewer);
  const path = await trail(slug);
  const space = spaces.find((s) => s.slug === path[0]?.slug);
  const visible = space ? flatten([space]) : [];
  const here = visible.findIndex((n) => n.slug === slug);
  const readable = canRead(node.audience, viewer);
  const doc = readable ? await loadDoc(slug, viewer) : null;
  if (readable && !doc) notFound();
  const visibleNode = visible[here] ?? node;

  return (
    <DocsShell spaces={spaces} activeSlug={slug} viewer={viewer} headings={doc?.headings}>
      <div className="mx-auto max-w-3xl">
        <Breadcrumbs trail={path} />
        <PageHeader node={node} space={path.length > 1 ? space : undefined} />
        {doc ? <DocsMarkdown content={doc.content} /> : <LockedNotice node={node} slug={slug} />}
        {node.kind !== "page" && (
          <ChildCards nodes={visibleNode.children} heading={node.kind === "space" ? "Pages in this space" : "In this section"} />
        )}
        <PrevNext prev={here > 0 ? visible[here - 1] : undefined} next={here >= 0 ? visible[here + 1] : undefined} />
        {doc && <PageFooter node={node} developer={viewer.platformOperator} />}
      </div>
    </DocsShell>
  );
}
