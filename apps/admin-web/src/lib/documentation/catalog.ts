import "server-only";

import fs from "fs/promises";
import path from "path";
import { docsRoot } from "./assets";
import { canRead, type DocAudience, type DocsNode, type DocsViewer } from "./types";

/**
 * The documentation catalog, built from the files themselves: every page under docs/ starts with front matter
 * (title, summary, order, audience). A top-level folder is a space, a folder inside it a section; a folder's
 * index.md describes it. docs/README.md is the GitHub index and not part of the catalog.
 */

const FRONT_MATTER = /^﻿?---\r?\n([\s\S]*?)\r?\n---\r?\n/;

/** Splits YAML front matter (only simple `key: value` lines are used) from the Markdown body. */
export function splitFrontMatter(raw: string): { fields: Record<string, string>; body: string } {
  const match = FRONT_MATTER.exec(raw);
  if (!match) return { fields: {}, body: raw.replace(/^﻿/, "") };
  const fields: Record<string, string> = {};
  for (const line of match[1].split(/\r?\n/)) {
    const pair = /^([A-Za-z][\w-]*):\s*(.*?)\s*$/.exec(line);
    if (pair) fields[pair[1]] = pair[2].replace(/^["']|["']$/g, "");
  }
  return { fields, body: raw.slice(match[0].length) };
}

function audienceOf(value: string | undefined): DocAudience {
  // No valid audience: treated as internal, so nothing is published by accident.
  return value === "public" || value === "operator" ? value : "internal";
}

export function slugForFile(file: string): string {
  const withoutExt = file.replace(/\\/g, "/").replace(/\.md$/i, "");
  return withoutExt.endsWith("/index") ? withoutExt.slice(0, -"/index".length) : withoutExt;
}

type Catalog = { spaces: DocsNode[]; bySlug: Map<string, DocsNode>; parentOf: Map<string, DocsNode> };

let cached: { at: number; catalog: Catalog } | null = null;
// The image's docs never change while it runs; in development pick up edits within a couple of seconds.
const TTL_MS = process.env.NODE_ENV === "production" ? Number.POSITIVE_INFINITY : 2000;

async function readNode(file: string, kind: DocsNode["kind"]): Promise<DocsNode> {
  const raw = await fs.readFile(path.join(/*turbopackIgnore: true*/ docsRoot(), file), "utf8");
  const { fields } = splitFrontMatter(raw);
  const order = Number.parseInt(fields.order ?? "", 10);
  const slug = slugForFile(file);
  return {
    slug,
    title: fields.title || slug.split("/").pop() || slug,
    summary: fields.summary ?? "",
    order: Number.isFinite(order) ? order : 999,
    audience: audienceOf(fields.audience),
    file,
    kind,
    children: [],
  };
}

async function readFolder(folder: string, kind: "space" | "section"): Promise<DocsNode | null> {
  const dir = path.join(/*turbopackIgnore: true*/ docsRoot(), folder);
  const entries = await fs.readdir(dir, { withFileTypes: true });
  if (!entries.some((e) => e.isFile() && e.name === "index.md")) return null;
  const node = await readNode(`${folder}/index.md`, kind);
  for (const entry of entries) {
    if (entry.isDirectory()) {
      const child = await readFolder(`${folder}/${entry.name}`, "section");
      if (child) node.children.push(child);
    } else if (entry.isFile() && entry.name.endsWith(".md") && entry.name !== "index.md") {
      node.children.push(await readNode(`${folder}/${entry.name}`, "page"));
    }
  }
  node.children.sort((a, b) => a.order - b.order || a.title.localeCompare(b.title));
  return node;
}

async function buildCatalog(): Promise<Catalog> {
  const entries = await fs.readdir(docsRoot(), { withFileTypes: true });
  const spaces: DocsNode[] = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const space = await readFolder(entry.name, "space");
    if (space) spaces.push(space);
  }
  spaces.sort((a, b) => a.order - b.order || a.title.localeCompare(b.title));
  const bySlug = new Map<string, DocsNode>();
  const parentOf = new Map<string, DocsNode>();
  const walk = (node: DocsNode, parent?: DocsNode) => {
    bySlug.set(node.slug, node);
    if (parent) parentOf.set(node.slug, parent);
    node.children.forEach((child) => walk(child, node));
  };
  spaces.forEach((space) => walk(space));
  return { spaces, bySlug, parentOf };
}

async function catalog(): Promise<Catalog> {
  if (cached && Date.now() - cached.at < TTL_MS) return cached.catalog;
  const built = await buildCatalog();
  cached = { at: Date.now(), catalog: built };
  return built;
}

/** The spaces a viewer may see: developer pages hidden unless allowed; operator pages shown locked when signed out. */
export async function getSpaces(viewer: DocsViewer): Promise<DocsNode[]> {
  const filter = (node: DocsNode): DocsNode | null => {
    if (node.audience === "internal" && !viewer.platformOperator) return null;
    return {
      ...node,
      locked: !canRead(node.audience, viewer),
      children: node.children.map(filter).filter((n): n is DocsNode => n !== null),
    };
  };
  return (await catalog()).spaces.map(filter).filter((n): n is DocsNode => n !== null);
}

export async function findNode(slug: string): Promise<DocsNode | undefined> {
  return (await catalog()).bySlug.get(slug);
}

/** Space › section › … › the node itself. */
export async function trail(slug: string): Promise<DocsNode[]> {
  const { bySlug, parentOf } = await catalog();
  const out: DocsNode[] = [];
  let node = bySlug.get(slug);
  while (node) {
    out.unshift(node);
    node = parentOf.get(node.slug);
  }
  return out;
}

/** Every node of a tree in reading order (depth first). */
export function flatten(nodes: DocsNode[]): DocsNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)]);
}

export async function allNodes(): Promise<DocsNode[]> {
  return flatten((await catalog()).spaces);
}

/** Page that shows each draw.io file inline (old /documentation/diagram/… links redirect there). */
const DRAWIO_PAGE: Record<string, string> = {
  "logical-architecture.drawio": "architecture/architecture",
  "auth-flow-authorization-code-pkce.drawio": "architecture/architecture",
  "auth-flow-client-credentials.drawio": "architecture/architecture",
  "auth-flow-refresh-rotation.drawio": "architecture/architecture",
  "auth-flow-hosted-vs-native.drawio": "architecture/architecture",
  "auth-flow-device-code.drawio": "architecture/architecture",
  "auth-flow-saml.drawio": "architecture/architecture",
  "deployment-mvp.drawio": "architecture/architecture",
  "deployment-production.drawio": "architecture/architecture",
  "data-layer-layering.drawio": "developer/database",
  "tenant-isolation.drawio": "developer/database",
  "enterprise-capability-map.drawio": "architecture/enterprise",
  "config-inheritance.drawio": "install/admin-control",
  "roles-permissions.drawio": "developer/data-model",
};

export function drawioDocumentSlug(assetPath: string): string | undefined {
  return DRAWIO_PAGE[assetPath.split("/").pop() ?? assetPath];
}

export const DOCS_HOME_SLUG = "";
