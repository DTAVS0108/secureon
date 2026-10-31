"use client";

import { Children, isValidElement, useRef, useState, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import Link from "next/link";
import { DrawioEmbed, HiddenDrawio } from "./DrawioEmbed";
import { FullscreenFrame } from "./FullscreenFrame";
import { MermaidDiagram } from "./MermaidDiagram";
import { CheckIcon, CopyIcon, LinkIcon } from "./DocsIcons";
import { withBasePath } from "@/lib/config";
import { createSlugger } from "@/lib/documentation/types";

function drawioTitleFromPath(assetPath: string): string {
  return (assetPath.split("/").pop() ?? "diagram").replace(/\.drawio$/i, "").replace(/-/g, " ");
}

function isDiagramCodeBlock(children: ReactNode): boolean {
  const child = Children.toArray(children)[0];
  if (!isValidElement<{ className?: string }>(child)) return false;
  const className = child.props.className ?? "";
  return className.includes("language-drawio") || className.includes("language-mermaid");
}

function textOf(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(textOf).join("");
  if (isValidElement<{ children?: ReactNode }>(node)) return textOf(node.props.children);
  return "";
}

type MdNode = { type: string; value?: string; children?: MdNode[]; data?: Record<string, unknown> };

const CALLOUTS = {
  note: "Note",
  tip: "Tip",
  important: "Important",
  warning: "Warning",
  caution: "Caution",
  developer: "Developer only · not shown to users or on the website",
} as const;

/** GitHub-style alerts (> [!NOTE], > [!TIP], > [!WARNING] …) become coloured panels. */
function remarkCallouts() {
  const visit = (node: MdNode) => {
    if (node.type === "blockquote") {
      const first = node.children?.[0];
      const text = first?.type === "paragraph" ? first.children?.[0] : undefined;
      const m = text?.type === "text" ? /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION|DEVELOPER)\][^\S\n]*\n?/i.exec(text.value ?? "") : null;
      if (m && text && first) {
        text.value = (text.value ?? "").slice(m[0].length);
        if (!text.value && first.children) first.children.shift();
        node.data = { ...node.data, hProperties: { "data-callout": m[1].toLowerCase() } };
      }
    }
    node.children?.forEach(visit);
  };
  return (tree: MdNode) => visit(tree);
}

function CodeBlock({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLPreElement>(null);
  const [copied, setCopied] = useState(false);
  return (
    <div className="group relative my-4">
      <pre ref={ref} className="docs-code overflow-x-auto rounded-xl border border-ui p-4 text-[13px] leading-relaxed">
        {children}
      </pre>
      <button
        type="button"
        onClick={() => {
          void navigator.clipboard?.writeText(ref.current?.innerText ?? "").then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
          });
        }}
        className="absolute right-2 top-2 flex items-center gap-1 rounded-md border border-ui bg-ui-surface px-2 py-1 text-[11px] text-muted opacity-0 transition-opacity hover:text-ui focus:opacity-100 group-hover:opacity-100"
        aria-label="Copy code"
      >
        {copied ? <CheckIcon className="h-3.5 w-3.5" /> : <CopyIcon className="h-3.5 w-3.5" />}
        {copied ? "Copied" : "Copy"}
      </button>
    </div>
  );
}

export function DocsMarkdown({ content }: { content: string }) {
  // Same ids as the server's "On this page" list: one counter for every heading, in order.
  const slug = createSlugger();
  const heading = (Tag: "h1" | "h2" | "h3" | "h4" | "h5" | "h6") =>
    function Heading({ children }: { children?: ReactNode }) {
      const id = slug(textOf(children));
      return (
        <Tag id={id} className="group scroll-mt-20">
          {children}
          <a href={`#${id}`} className="docs-anchor" aria-label="Link to this section">
            <LinkIcon className="h-4 w-4" />
          </a>
        </Tag>
      );
    };

  return (
    <article className="docs-prose min-w-0 max-w-none">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkCallouts]}
        components={{
          h1: heading("h1"),
          h2: heading("h2"),
          h3: heading("h3"),
          h4: heading("h4"),
          h5: heading("h5"),
          h6: heading("h6"),
          blockquote: (props) => {
            const kind = (props as { "data-callout"?: keyof typeof CALLOUTS })["data-callout"];
            if (kind && CALLOUTS[kind]) {
              return (
                <div className={`docs-callout docs-callout-${kind}`} role="note">
                  <p className="docs-callout-title">{CALLOUTS[kind]}</p>
                  {props.children}
                </div>
              );
            }
            return <blockquote>{props.children}</blockquote>;
          },
          a: ({ href, children }) => {
            if (href?.startsWith("/api/documentation/assets/")) {
              return (
                <a href={withBasePath(href)} target="_blank" rel="noopener noreferrer" download>
                  {children}
                </a>
              );
            }
            if (href?.startsWith("/documentation")) return <Link href={href}>{children}</Link>;
            // Other site paths (/admin/…, /docs, /.well-known/…) belong to the SecureOne server, not the console.
            if (href?.startsWith("/")) return <a href={href}>{children}</a>;
            const external = href?.startsWith("http");
            return (
              <a href={href} target={external ? "_blank" : undefined} rel={external ? "noopener noreferrer" : undefined}>
                {children}
              </a>
            );
          },
          img: ({ src, alt }) =>
            typeof src === "string" ? (
              <FullscreenFrame className="my-4 inline-block rounded-lg border border-ui bg-white p-2">
                {(fullscreen) => (
                  // eslint-disable-next-line @next/next/no-img-element -- docs images are served from /api/documentation/assets
                  <img
                    src={src.startsWith("/") ? withBasePath(src) : src}
                    alt={alt ?? ""}
                    className={fullscreen ? undefined : "max-w-full"}
                  />
                )}
              </FullscreenFrame>
            ) : null,
          table: ({ children }) => (
            <div className="my-5 overflow-x-auto rounded-xl border border-ui">
              <table className="w-full min-w-[480px] border-collapse text-sm">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border-b border-ui bg-ui-elevated px-3 py-2 text-left font-semibold text-ui">{children}</th>
          ),
          td: ({ children }) => <td className="border-b border-ui px-3 py-2 align-top text-soft">{children}</td>,
          pre: ({ children }) => {
            if (isDiagramCodeBlock(children)) return <>{children}</>;
            return <CodeBlock>{children}</CodeBlock>;
          },
          code: ({ className, children }) => {
            if (className?.includes("language-drawio-hidden")) {
              const assetPath = String(children).replace(/\n$/, "").trim();
              return <HiddenDrawio assetPath={assetPath} title={drawioTitleFromPath(assetPath)} />;
            }
            if (className?.includes("language-drawio")) {
              const assetPath = String(children).replace(/\n$/, "").trim();
              return <DrawioEmbed assetPath={assetPath} title={drawioTitleFromPath(assetPath)} />;
            }
            if (className?.includes("language-mermaid")) {
              return <MermaidDiagram source={String(children).replace(/\n$/, "").trim()} />;
            }
            if (!className) return <code className="docs-inline-code">{children}</code>;
            return <code className={className}>{children}</code>;
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </article>
  );
}
