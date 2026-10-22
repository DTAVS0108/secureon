import { cn } from "@/lib/cn";

export type BadgeTone =
  | "neutral"
  | "success"
  | "warning"
  | "danger"
  | "info"
  | "indigo";

const TONES: Record<BadgeTone, string> = {
  neutral: "bg-ui-elevated text-soft",
  success: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
  warning: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
  danger: "bg-red-500/15 text-red-700 dark:text-red-300",
  info: "bg-sky-500/15 text-sky-700 dark:text-sky-300",
  indigo: "bg-brand-muted text-brand",
};

export function Badge({
  children,
  tone = "neutral",
  dot = false,
  className,
  "data-testid": testId,
}: {
  children: React.ReactNode;
  tone?: BadgeTone;
  dot?: boolean;
  className?: string;
  "data-testid"?: string;
}) {
  return (
    <span
      data-testid={testId}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap",
        TONES[tone],
        className,
      )}
    >
      {dot && (
        <span
          className={cn(
            "h-1.5 w-1.5 rounded-full",
            tone === "success" && "bg-emerald-500",
            tone === "warning" && "bg-amber-500",
            tone === "danger" && "bg-red-500",
            tone === "info" && "bg-sky-500",
            tone === "indigo" && "bg-brand",
            tone === "neutral" && "bg-black/40 dark:bg-white/40",
          )}
        />
      )}
      {children}
    </span>
  );
}

/** Marks a feature that is shown but not built yet; pair it with a disabled control. */
export function ComingSoonBadge({ className }: { className?: string }) {
  return (
    <Badge tone="warning" className={className}>
      Coming soon
    </Badge>
  );
}
