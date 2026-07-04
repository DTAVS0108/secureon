"use client";

import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { cn } from "@/lib/cn";

export function Toggle({
  defaultOn = false,
  checked,
  disabled,
  onChange,
  "aria-label": ariaLabel,
}: {
  defaultOn?: boolean;
  /** Controlled mode — syncs when parent value changes */
  checked?: boolean;
  disabled?: boolean;
  onChange?: (on: boolean) => void;
  "aria-label"?: string;
}) {
  const controlled = checked !== undefined;
  const [on, setOn] = useState(controlled ? checked : defaultOn);

  useDeferredEffect(() => {
    if (controlled) setOn(checked);
  }, [controlled, checked]);

  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={() => {
        if (disabled) return;
        const next = !on;
        if (!controlled) setOn(next);
        onChange?.(next);
      }}
      className={cn(
        "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand",
        on ? "bg-brand" : "bg-black/15 dark:bg-white/20",
        disabled && "cursor-not-allowed opacity-50",
      )}
    >
      <span
        className={cn(
          "inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform",
          on ? "translate-x-5" : "translate-x-0.5",
        )}
      />
    </button>
  );
}
