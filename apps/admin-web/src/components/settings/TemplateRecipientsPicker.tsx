"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { cn } from "@/lib/cn";
import type { AdminRecipientOption, RecipientUserOption } from "@/lib/actions/settings";
import {
  ADMIN_RECIPIENTS_TOKEN,
  groupToken,
  labelRecipientEntry,
  parseGroupToken,
} from "@/lib/email-recipient-tokens";
import type { RecipientGroups } from "@/lib/api/settings";

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function TemplateRecipientsPicker({
  selected,
  onChange,
  recipientGroups,
  adminOptions,
  allUsers,
  disabled,
  ariaLabel,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  recipientGroups: RecipientGroups;
  adminOptions: AdminRecipientOption[];
  allUsers: RecipientUserOption[];
  disabled?: boolean;
  ariaLabel: string;
}) {
  const [open, setOpen] = useState(false);
  const [customEmail, setCustomEmail] = useState("");
  const [customError, setCustomError] = useState<string | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();

  const emailLabels = useMemo(() => {
    const labels: Record<string, string> = {};
    for (const option of adminOptions) {
      labels[option.email] = option.label;
    }
    for (const user of allUsers) {
      labels[user.email] = user.label;
    }
    return labels;
  }, [adminOptions, allUsers]);

  const groupEntries = useMemo(() => Object.entries(recipientGroups), [recipientGroups]);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [open]);

  function toggleEntry(value: string, checked: boolean) {
    if (checked) {
      if (selected.includes(value)) return;
      onChange([...selected, value]);
      return;
    }
    onChange(selected.filter((entry) => entry !== value));
  }

  function addCustomEmail() {
    const email = normalizeEmail(customEmail);
    if (!email) {
      setCustomError("Enter an email address.");
      return;
    }
    if (!isValidEmail(email)) {
      setCustomError("Enter a valid email address.");
      return;
    }
    if (selected.includes(email)) {
      setCustomError("That recipient is already selected.");
      return;
    }
    onChange([...selected, email]);
    setCustomEmail("");
    setCustomError(null);
  }

  const triggerLabel =
    selected.length === 0
      ? "Select recipients…"
      : `${selected.length} recipient${selected.length === 1 ? "" : "s"} selected`;

  const datalistId = `${listId}-users`;

  return (
    <div ref={rootRef} className={cn("space-y-2", disabled && "pointer-events-none opacity-60")}>
      {selected.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label={ariaLabel}>
          {selected.map((entry) => (
            <li key={entry}>
              <span className="inline-flex max-w-full items-center gap-1 rounded-full border border-ui bg-ui-elevated py-0.5 pl-2.5 pr-1 text-xs text-ui">
                <span className="truncate" title={entry}>
                  {labelRecipientEntry(entry, recipientGroups, emailLabels)}
                </span>
                <button
                  type="button"
                  className="rounded-full px-1 text-faint hover:bg-black/10 hover:text-ui dark:hover:bg-white/10"
                  aria-label={`Remove ${entry}`}
                  onClick={() => toggleEntry(entry, false)}
                >
                  ×
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="relative">
        <button
          type="button"
          aria-expanded={open}
          aria-controls={listId}
          disabled={disabled}
          onClick={() => setOpen((value) => !value)}
          className="flex w-full items-center justify-between gap-2 rounded-[var(--ui-radius)] border border-ui bg-ui-elevated px-3 py-2 text-left text-sm text-ui outline-none transition-colors hover:bg-black/[0.02] focus:border-[var(--ui-primary)] focus:ring-2 focus:ring-brand dark:hover:bg-white/[0.03]"
        >
          <span className={selected.length === 0 ? "text-muted" : "font-medium"}>{triggerLabel}</span>
          <span className="text-faint" aria-hidden>
            {open ? "▴" : "▾"}
          </span>
        </button>

        {open && (
          <div
            id={listId}
            className="absolute z-20 mt-1 w-full rounded-[var(--ui-radius)] border border-ui bg-ui-surface shadow-lg"
          >
            <div className="max-h-64 overflow-y-auto p-2">
              <p className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wide text-faint">
                Lists & groups
              </p>
              <label className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-2 text-sm hover:bg-ui-elevated">
                <input
                  type="checkbox"
                  className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--ui-primary)]"
                  checked={selected.includes(ADMIN_RECIPIENTS_TOKEN)}
                  onChange={(e) => toggleEntry(ADMIN_RECIPIENTS_TOKEN, e.target.checked)}
                />
                <span className="min-w-0">
                  <span className="block font-medium text-ui">All admin recipients</span>
                  <span className="block text-xs text-muted">
                    Uses the Admin recipients list configured above
                  </span>
                </span>
              </label>
              {groupEntries.map(([name, emails]) => {
                const token = groupToken(name);
                const checked = selected.some((entry) => parseGroupToken(entry)?.toLowerCase() === name.toLowerCase());
                return (
                  <label
                    key={name}
                    className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-2 text-sm hover:bg-ui-elevated"
                  >
                    <input
                      type="checkbox"
                      className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--ui-primary)]"
                      checked={checked}
                      onChange={(e) => toggleEntry(token, e.target.checked)}
                    />
                    <span className="min-w-0">
                      <span className="block font-medium text-ui">Group: {name}</span>
                      <span className="block truncate text-xs text-muted">
                        {(emails ?? []).length > 0
                          ? (emails ?? []).join(", ")
                          : "No emails in this group yet"}
                      </span>
                    </span>
                  </label>
                );
              })}
              {groupEntries.length === 0 && (
                <p className="px-2 py-1 text-xs text-muted">
                  Add recipient groups above to reference them here.
                </p>
              )}

              <p className="px-2 pb-1 pt-3 text-[10px] font-semibold uppercase tracking-wide text-faint">
                Individual emails
              </p>
              {adminOptions.length === 0 ? (
                <p className="px-2 py-1 text-xs text-muted">No admin-role users found.</p>
              ) : (
                adminOptions.map((option) => {
                  const checked = selected.includes(option.email);
                  return (
                    <label
                      key={option.id}
                      className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-2 text-sm hover:bg-ui-elevated"
                    >
                      <input
                        type="checkbox"
                        className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--ui-primary)]"
                        checked={checked}
                        onChange={(e) => toggleEntry(option.email, e.target.checked)}
                      />
                      <span className="min-w-0">
                        <span className="block font-medium text-ui">{option.label}</span>
                        <span className="block truncate text-xs text-muted">{option.email}</span>
                      </span>
                    </label>
                  );
                })
              )}
            </div>

            <div className="border-t border-ui p-3">
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-faint">
                Custom email
              </p>
              <div className="flex flex-wrap gap-2">
                <div className="min-w-0 flex-1">
                  <Input
                    type="email"
                    list={allUsers.length > 0 ? datalistId : undefined}
                    value={customEmail}
                    placeholder="Email address"
                    onChange={(e) => {
                      setCustomEmail(e.target.value);
                      setCustomError(null);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        addCustomEmail();
                      }
                    }}
                  />
                  {allUsers.length > 0 && (
                    <datalist id={datalistId}>
                      {allUsers.map((user) => (
                        <option key={user.id} value={user.email}>
                          {user.label}
                        </option>
                      ))}
                    </datalist>
                  )}
                </div>
                <Button type="button" variant="secondary" size="sm" onClick={addCustomEmail}>
                  Add
                </Button>
              </div>
              {customError && <p className="mt-1.5 text-xs text-red-600 dark:text-red-400">{customError}</p>}
              <p className="mt-1.5 text-xs text-muted">
                Save notification settings above before using @admin or group tokens. If BCC is the same address as
                your SMTP sender, a separate copy email is delivered automatically.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
