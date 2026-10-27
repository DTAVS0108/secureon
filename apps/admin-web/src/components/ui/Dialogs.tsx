"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "./Button";

export type ConfirmOptions = {
  title: string;
  message?: React.ReactNode;
  confirmLabel?: string;
  /** "danger" (red button) for removals and sign-outs; "primary" otherwise. */
  tone?: "danger" | "primary";
};

export type PromptOptions = ConfirmOptions & {
  label: string;
  defaultValue?: string;
  placeholder?: string;
  inputType?: "text" | "number";
  /** A message when the value cannot be used (the dialog stays open), null when it can. */
  validate?: (value: string) => string | null;
};

type Request =
  | { id: number; kind: "confirm"; options: ConfirmOptions; resolve: (ok: boolean) => void }
  | { id: number; kind: "prompt"; options: PromptOptions; resolve: (value: string | null) => void };

type Dialogs = {
  /** Resolves true when the user confirms, false when they cancel. */
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  /** Resolves the value entered, or null when the user cancels. */
  prompt: (options: PromptOptions) => Promise<string | null>;
};

const DialogContext = createContext<Dialogs | null>(null);

/** Console dialogs for questions asked in the middle of an action (in place of window.confirm and window.prompt). */
export function DialogProvider({ children }: { children: React.ReactNode }) {
  const [request, setRequest] = useState<Request | null>(null);
  const next = useRef(0);

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => setRequest({ id: ++next.current, kind: "confirm", options, resolve })),
    [],
  );
  const prompt = useCallback(
    (options: PromptOptions) =>
      new Promise<string | null>((resolve) => setRequest({ id: ++next.current, kind: "prompt", options, resolve })),
    [],
  );

  return (
    <DialogContext.Provider value={{ confirm, prompt }}>
      {children}
      {request && <DialogView key={request.id} request={request} done={() => setRequest(null)} />}
    </DialogContext.Provider>
  );
}

export function useDialog(): Dialogs {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error("useDialog must be used within DialogProvider");
  return ctx;
}

function DialogView({ request, done }: { request: Request; done: () => void }) {
  const { options } = request;
  const prompt = request.kind === "prompt" ? request.options : null;
  const [value, setValue] = useState(prompt?.defaultValue ?? "");
  const [error, setError] = useState<string | null>(null);

  const cancel = useCallback(() => {
    if (request.kind === "confirm") request.resolve(false);
    else request.resolve(null);
    done();
  }, [request, done]);

  function accept() {
    if (request.kind === "confirm") {
      request.resolve(true);
      done();
      return;
    }
    const problem = request.options.validate?.(value) ?? null;
    if (problem) {
      setError(problem);
      return;
    }
    request.resolve(value);
    done();
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && cancel();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [cancel]);

  const tone = options.tone ?? (request.kind === "confirm" ? "danger" : "primary");

  return createPortal(
    // On <body>, so no card or panel on the page can paint over the dialog.
    <div className="fixed inset-0 z-100 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={cancel} />
      <form
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="console-dialog-title"
        className="relative z-10 w-full max-w-md rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
        onSubmit={(e) => {
          e.preventDefault();
          accept();
        }}
      >
        <h2 id="console-dialog-title" className="text-base font-semibold">
          {options.title}
        </h2>
        {options.message && <div className="mt-2 text-sm text-black/60 dark:text-white/60">{options.message}</div>}
        {prompt && (
          <label className="mt-4 block text-sm">
            <span className="text-black/70 dark:text-white/70">{prompt.label}</span>
            <input
              autoFocus
              type={prompt.inputType ?? "text"}
              value={value}
              placeholder={prompt.placeholder}
              onChange={(e) => {
                setValue(e.target.value);
                setError(null);
              }}
              autoComplete="off"
              className="mt-1 block w-full rounded-md border border-ui bg-ui-elevated px-3 py-2 text-sm text-ui outline-none focus:border-ui-primary"
            />
          </label>
        )}
        {error && (
          <p role="alert" className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
            {error}
          </p>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={cancel}>
            Cancel
          </Button>
          <Button type="submit" variant={tone} autoFocus={!prompt}>
            {options.confirmLabel ?? (request.kind === "confirm" ? "Confirm" : "OK")}
          </Button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
