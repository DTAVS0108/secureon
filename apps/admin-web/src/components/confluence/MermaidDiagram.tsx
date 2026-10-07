"use client";

import { useId, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

function mermaidElementId(id: string): string {
  return `mermaid-${id.replace(/[^a-zA-Z0-9_-]/g, "")}`;
}

export function MermaidDiagram({ source }: { source: string }) {
  const reactId = useId();
  const [svg, setSvg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useDeferredEffect(() => {
    let cancelled = false;

    async function renderDiagram() {
      try {
        const { default: mermaid } = await import("mermaid");
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "strict",
          theme: "neutral",
          fontFamily: "system-ui, sans-serif",
        });
        const result = await mermaid.render(mermaidElementId(reactId), source);
        if (!cancelled) {
          setSvg(result.svg);
          setError(null);
        }
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : "Could not render Mermaid diagram");
        }
      }
    }

    void renderDiagram();
    return () => {
      cancelled = true;
    };
  }, [reactId, source]);

  if (error) {
    return (
      <div
        data-mermaid-status="error"
        className="my-4 rounded-lg border border-red-500/30 bg-red-500/10 p-4"
      >
        <p className="mb-2 text-sm text-red-600 dark:text-red-400">Could not render diagram: {error}</p>
        <pre className="overflow-x-auto whitespace-pre-wrap text-xs">{source}</pre>
      </div>
    );
  }

  if (!svg) {
    return (
      <div
        data-mermaid-status="loading"
        className="my-4 flex min-h-48 items-center justify-center rounded-lg border border-ui bg-ui-surface text-sm text-muted"
      >
        Rendering diagram…
      </div>
    );
  }

  return (
    <figure
      data-mermaid-status="ready"
      className="mermaid-diagram my-4 overflow-x-auto rounded-lg border border-ui bg-white p-4"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
