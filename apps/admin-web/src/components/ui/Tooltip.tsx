import { cn } from "@/lib/cn";

/**
 * Hover and keyboard-focus tooltip around a control (a button, link or modal trigger). Pure CSS, so it
 * also works in server components. For an "i" icon beside a label use {@link InfoTip}.
 */
export function Tooltip({
  content,
  children,
  side = "bottom",
  align = "center",
  className,
}: {
  content: string;
  children: React.ReactNode;
  side?: "top" | "bottom";
  /** Which edge of the control the tooltip lines up with; use "end" near the right edge of the page. */
  align?: "start" | "center" | "end";
  className?: string;
}) {
  return (
    <span className={cn("group/tip relative inline-flex", className)}>
      {children}
      <span
        role="tooltip"
        className={cn(
          "pointer-events-none absolute z-50 w-72 rounded-md border border-ui bg-ui-elevated px-3 py-2 text-left text-xs font-normal leading-relaxed text-[var(--ui-text)] opacity-0 shadow-lg transition-opacity delay-200 duration-150",
          "group-hover/tip:opacity-100 group-focus-within/tip:opacity-100",
          side === "bottom" ? "top-full mt-2" : "bottom-full mb-2",
          align === "start" && "left-0",
          align === "center" && "left-1/2 -translate-x-1/2",
          align === "end" && "right-0",
        )}
      >
        {content}
      </span>
    </span>
  );
}
