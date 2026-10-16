"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { withBasePath } from "@/lib/config";

const DRAWIO_EMBED_ORIGIN = "https://embed.diagrams.net";
const DRAWIO_EMBED_URL = `${DRAWIO_EMBED_ORIGIN}/?embed=1&ui=min&spin=1&proto=json&noSaveBtn=1&noExitBtn=1`;
const LOAD_TIMEOUT_MS = 20_000;

type DrawioEmbedProps = {
  assetPath: string;
  title: string;
};

export function DrawioEmbed({ assetPath, title }: DrawioEmbedProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const xmlRef = useRef<string | null>(null);
  const readyRef = useRef(false);
  const loadedRef = useRef(false);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  const sendLoad = useCallback(() => {
    if (loadedRef.current || !xmlRef.current || !readyRef.current || !iframeRef.current?.contentWindow) {
      return;
    }
    iframeRef.current.contentWindow.postMessage(
      JSON.stringify({
        action: "load",
        xml: xmlRef.current,
        autosave: 0,
        modified: false,
        readonly: true,
        title,
      }),
      DRAWIO_EMBED_ORIGIN,
    );
    loadedRef.current = true;
    setStatus("ready");
  }, [title]);

  useDeferredEffect(() => {
    let cancelled = false;
    loadedRef.current = false;
    readyRef.current = false;
    setStatus("loading");
    setError(null);

    const timeout = window.setTimeout(() => {
      if (cancelled || loadedRef.current) return;
      setStatus("error");
      setError("Diagram viewer timed out. Check your internet connection or download the source file.");
    }, LOAD_TIMEOUT_MS);

    fetch(withBasePath(`/api/confluence/assets/${assetPath}`), { credentials: "include" })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.text();
      })
      .then((xml) => {
        if (cancelled) return;
        xmlRef.current = xml;
        sendLoad();
      })
      .catch((e) => {
        if (cancelled) return;
        setStatus("error");
        setError(e instanceof Error ? e.message : "Failed to load diagram");
      });

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
    };
  }, [assetPath, sendLoad]);

  useDeferredEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (event.origin !== DRAWIO_EMBED_ORIGIN) return;
      if (event.source !== iframeRef.current?.contentWindow) return;
      if (typeof event.data !== "string" || event.data.length === 0) return;
      try {
        const msg = JSON.parse(event.data) as { event?: string };
        if (msg.event === "init") {
          readyRef.current = true;
          sendLoad();
        }
      } catch {
        // ignore non-JSON messages from draw.io
      }
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [sendLoad]);

  if (status === "error") {
    return (
      <div className="my-4 rounded-lg border border-red-500/30 bg-red-500/10 p-6 text-sm text-red-600 dark:text-red-400">
        <p>Could not load diagram: {error ?? "unknown error"}</p>
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
    <div className="relative my-4 min-h-[420px] w-full overflow-hidden rounded-lg border border-ui bg-ui-surface">
      {status === "loading" && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-ui-surface/80 text-sm text-muted">
          Loading diagram…
        </div>
      )}
      <iframe
        ref={iframeRef}
        title={title}
        src={DRAWIO_EMBED_URL}
        className="h-[min(70vh,720px)] w-full border-0"
        allowFullScreen
      />
    </div>
  );
}
