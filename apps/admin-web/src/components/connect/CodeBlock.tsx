"use client";

import { CopyValue } from "@/components/ui/CopyValue";

/** A titled, copyable code sample (setup code for Connect an app and the client page). */
export function CodeBlock({ title, language, code }: { title: string; language: string; code: string }) {
  return (
    <div className="overflow-hidden rounded-lg border border-ui">
      <div className="flex items-center justify-between gap-2 border-b border-ui bg-black/5 px-3 py-1.5 dark:bg-white/5">
        <span className="font-mono text-xs text-muted">{title}</span>
        <CopyValue value={code} />
      </div>
      <pre className="max-h-112 overflow-auto p-3 font-mono text-[12px] leading-relaxed text-ui" data-language={language}>
        <code>{code}</code>
      </pre>
    </div>
  );
}
