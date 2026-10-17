import { InfoTip } from "@/components/ui/InfoTip";
import { cn } from "@/lib/cn";

export function Card({
  children,
  className,
  padded = true,
}: {
  children: React.ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <section
      className={cn(
        "rounded-[var(--ui-radius)] border border-ui bg-ui-surface text-[var(--ui-text)] my-4",
        padded && "p-5",
        className,
      )}
    >
      {children}
    </section>
  );
}

export function CardHeader({
  title,
  description,
  action,
  info,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  info?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-ui px-5 py-4">
      <div>
        <h2 className="flex items-center text-sm font-semibold text-[var(--ui-text)]">
          {title}
          {info && <InfoTip content={info} />}
        </h2>
        {description && (
          <p className="mt-0.5 text-xs text-muted">{description}</p>
        )}
      </div>
      {action}
    </div>
  );
}
