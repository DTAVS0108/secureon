import { cloneElement, isValidElement, useId } from "react";
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

/**
 * Label + control. When the control is a single element without an id, it gets one and the label
 * points at it, so screen readers announce the field name and clicking the label focuses it.
 */
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
  const generatedId = useId();
  let control = children;
  let target = htmlFor;
  // Only form controls (inputs, selects, or components rendering one) can be label targets; a wrapper
  // div holding several controls must pass htmlFor for the one the label names.
  if (!target && isValidElement<{ id?: string }>(children) && children.type !== "div") {
    target = children.props.id ?? generatedId;
    if (!children.props.id) control = cloneElement(children, { id: target });
  }
  return (
    <div>
      <Label htmlFor={target} hint={hint} info={info}>
        {label}
      </Label>
      {control}
    </div>
  );
}
