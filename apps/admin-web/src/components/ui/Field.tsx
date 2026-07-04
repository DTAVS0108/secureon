import { cn } from "@/lib/cn";
import { InfoTip } from "@/components/ui/InfoTip";

const inputBase =
  "w-full rounded-[var(--ui-radius)] border border-ui bg-ui-elevated px-3 py-2 text-sm text-[var(--ui-text)] outline-none transition-colors placeholder-ui focus:border-[var(--ui-primary)] focus:ring-2 focus:ring-brand";

export function Label({
  children,
  htmlFor,
  hint,
  info,
}: {
  children: React.ReactNode;
  htmlFor?: string;
  hint?: string;
  info?: string;
}) {
  return (
    <label htmlFor={htmlFor} className="mb-1.5 flex items-center text-sm font-medium text-[var(--ui-text)]">
      <span>
        {children}
        {hint && <span className="ml-2 font-normal text-faint">{hint}</span>}
      </span>
      {info && <InfoTip content={info} />}
    </label>
  );
}

export function Input({
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(inputBase, className)} {...props} />;
}

export function Textarea({
  className,
  ...props
}: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(inputBase, "min-h-20", className)} {...props} />;
}

export function Select({
  className,
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cn(inputBase, "appearance-none", className)} {...props}>
      {children}
    </select>
  );
}

export function FieldRow({
  label,
  hint,
  info,
  htmlFor,
  children,
}: {
  label: string;
  hint?: string;
  info?: string;
  htmlFor?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <Label htmlFor={htmlFor} hint={hint} info={info}>
        {label}
      </Label>
      {children}
    </div>
  );
}
