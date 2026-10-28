"use client";

import { useEffect } from "react";

/**
 * Toolbar for the print-friendly document view. Auto-opens the browser
 * print dialog (where the user picks "Save as PDF") and offers a manual
 * button in case the popup is blocked. Hidden on the printed page itself.
 */
export function PdfPrintBar({ title }: { title: string }) {
  useEffect(() => {
    const startedAt = Date.now();
    const timeoutMs = 15_000;
    const timer = window.setInterval(() => {
      const diagramsLoading = document.querySelector('[data-mermaid-status="loading"]');
      if (!diagramsLoading || Date.now() - startedAt >= timeoutMs) {
        window.clearInterval(timer);
        window.print();
      }
    }, 250);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="print-hide sticky top-0 z-20 flex items-center justify-between gap-4 border-b border-ui bg-ui-surface px-6 py-3">
      <div className="min-w-0">
        <p className="text-xs font-semibold uppercase tracking-wider text-faint">Save as PDF</p>
        <p className="truncate text-sm text-soft">
          {title} — choose <span className="font-medium text-ui">“Save as PDF”</span> as the destination in the print
          dialog.
        </p>
      </div>
      <button
        type="button"
        onClick={() => window.print()}
        className="shrink-0 rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90"
        style={{ backgroundColor: "var(--ui-primary)" }}
      >
        Download PDF
      </button>
    </div>
  );
}
