"use client";

import { useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { withBasePath } from "@/lib/config";
import { cn } from "@/lib/cn";
import { FullscreenFrame } from "./FullscreenFrame";

/**
 * draw.io's read-only viewer library. It renders the diagram straight into the page; the iframe editor
 * (embed.diagrams.net) refuses to load inside other sites (HTTP 403), so it cannot be used here.
 */
const VIEWER_SCRIPT = "https://viewer.diagrams.net/js/viewer-static.min.js";
const LOAD_TIMEOUT_MS = 20_000;

type GraphViewerGlobal = { processElements: (className?: string) => void };

declare global {
  interface Window {
    GraphViewer?: GraphViewerGlobal;
  }
}

let viewerScript: Promise<GraphViewerGlobal> | null = null;

/** Loads the viewer script once per page, shared by every diagram on it. */
function loadViewer(): Promise<GraphViewerGlobal> {
  if (window.GraphViewer) return Promise.resolve(window.GraphViewer);
  if (!viewerScript) {
    viewerScript = new Promise<GraphViewerGlobal>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = VIEWER_SCRIPT;
      script.async = true;
      script.onload = () =>
        window.GraphViewer ? resolve(window.GraphViewer) : reject(new Error("Diagram viewer did not start."));
      script.onerror = () => reject(new Error("Could not load the diagram viewer."));
      document.head.appendChild(script);
    }).catch((e) => {
      viewerScript = null; // allow a retry on the next diagram
      throw e;
    });
  }
  return viewerScript;
}

/** Page names of a .drawio file; one file can hold several diagrams (for example three SAML flows). */
function pageNames(xml: string): string[] {
  const doc = new DOMParser().parseFromString(xml, "text/xml");
  return Array.from(doc.getElementsByTagName("diagram")).map((d, i) => d.getAttribute("name") || `Page ${i + 1}`);
}

type DrawioEmbedProps = {
  assetPath: string;
  title: string;
};

/**
 * A draw.io diagram the page also shows in Mermaid: collapsed by default, and the viewer only loads
 * when someone opens it.
 */
export function HiddenDrawio({ assetPath, title }: DrawioEmbedProps) {
  const [open, setOpen] = useState(false);
  return (
    <details className="my-2 text-sm" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="cursor-pointer text-muted hover:text-ui">Show draw.io version</summary>
      {open && <DrawioEmbed assetPath={assetPath} title={title} />}
    </details>
  );
}

export function DrawioEmbed({ assetPath, title }: DrawioEmbedProps) {
  const [xml, setXml] = useState<string | null>(null);
  const [pages, setPages] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useDeferredEffect(() => {
    let cancelled = false;
    setXml(null);
    setError(null);
    setPage(0);
    fetch(withBasePath(`/api/confluence/assets/${assetPath}`), { credentials: "include" })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.text();
      })
      .then((text) => {
        if (cancelled) return;
        setPages(pageNames(text));
        setXml(text);
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : "Failed to load diagram"));
    return () => {
      cancelled = true;
    };
  }, [assetPath]);

  if (error) {
    return (
      <div className="my-4 rounded-lg border border-red-500/30 bg-red-500/10 p-6 text-sm text-red-600 dark:text-red-400">
        <p>Could not load diagram: {error}</p>
        <a
          href={withBasePath(`/api/confluence/assets/${assetPath}`)}
          className="mt-2 inline-block text-brand hover:underline"
          download
        >
          Download source file
        </a>
      </div>
    );
  }

  return (
    <div className="my-4">
      {pages.length > 1 && (
        <div role="tablist" aria-label={`${title} pages`} className="mb-2 flex flex-wrap gap-1">
          {pages.map((name, i) => (
            <button
              key={`${i}-${name}`}
              type="button"
              role="tab"
              aria-selected={page === i}
              onClick={() => setPage(i)}
              className={cn(
                "rounded-md border px-3 py-1 text-xs font-medium transition-colors",
                page === i
                  ? "border-ui-primary bg-brand-muted text-brand"
                  : "border-ui text-muted hover:text-ui",
              )}
            >
              {name}
            </button>
          ))}
        </div>
      )}
      <FullscreenFrame className="rounded-lg border border-ui bg-white p-2">
        {(fullscreen) =>
          xml ? (
            <DrawioPage key={page} xml={xml} page={page} title={title} fullscreen={fullscreen} onError={setError} />
          ) : (
            <span className="flex min-h-40 items-center justify-center text-sm text-muted">Loading diagram…</span>
          )
        }
      </FullscreenFrame>
    </div>
  );
}

/**
 * One page of a diagram. The viewer fits it to the space it has (`responsive` + `auto-fit`): the page
 * width normally, the whole screen in full screen (where it may also zoom in). It is redrawn when that
 * changes, so the whole diagram is always visible.
 */
function DrawioPage({
  xml,
  page,
  title,
  fullscreen,
  onError,
}: {
  xml: string;
  page: number;
  title: string;
  fullscreen: boolean;
  onError: (message: string) => void;
}) {
  const hostRef = useRef<HTMLSpanElement>(null);
  // Width / height of the diagram, measured in the normal view; full screen uses it to fit the height too.
  const aspectRef = useRef<number | null>(null);

  useDeferredEffect(() => {
    let cancelled = false;
    const timeout = window.setTimeout(
      () => onError("Diagram viewer timed out. Check your internet connection or download the source file."),
      LOAD_TIMEOUT_MS,
    );
    loadViewer()
      .then((viewer) => {
        const host = hostRef.current;
        if (cancelled || !host) return;
        const graph = document.createElement("div");
        graph.className = "mxgraph";
        graph.style.width = "100%";
        graph.setAttribute(
          "data-mxgraph",
          JSON.stringify({
            xml,
            page,
            toolbar: "zoom layers",
            nav: true,
            lightbox: false,
            highlight: "#0000ff",
            center: true,
            responsive: true,
            "auto-fit": true,
            // Full screen: grow small diagrams to fill the screen.
            "allow-zoom-in": fullscreen,
          }),
        );
        if (fullscreen) {
          // auto-fit fits the width; narrow the box so the height also fits on screen.
          const aspect = aspectRef.current;
          const width = window.innerWidth - 48;
          host.style.width = `${aspect ? Math.min(width, (window.innerHeight - 96) * aspect) : width}px`;
        } else {
          host.style.width = "";
        }
        host.replaceChildren(graph);
        viewer.processElements();
        window.clearTimeout(timeout);
        if (!fullscreen) {
          requestAnimationFrame(() => {
            if (graph.offsetHeight > 0) aspectRef.current = graph.offsetWidth / graph.offsetHeight;
          });
        }
      })
      .catch((e) => {
        window.clearTimeout(timeout);
        if (!cancelled) onError(e instanceof Error ? e.message : "Failed to load diagram");
      });
    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
    };
  }, [xml, page, fullscreen]);

  return (
    <span
      ref={hostRef}
      role="img"
      aria-label={title}
      className="block w-full"
    />
  );
}
