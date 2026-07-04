"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { cn } from "@/lib/cn";
import type { AdminRecipientOption, RecipientUserOption } from "@/lib/actions/settings";

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function labelForEmail(
  email: string,
  adminOptions: AdminRecipientOption[],
  allUsers: RecipientUserOption[],
): string {
  const admin = adminOptions.find((o) => o.email === email);
  if (admin) return admin.label;
  const user = allUsers.find((o) => o.email === email);
  if (user) return user.label;
  return email;
}

export function AdminRecipientsPicker({
  adminOptions,
  allUsers,
  selectedEmails,
  onChange,
  disabled,
}: {
  adminOptions: AdminRecipientOption[];
  allUsers: RecipientUserOption[];
  selectedEmails: string[];
  onChange: (emails: string[]) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [customEmail, setCustomEmail] = useState("");
  const [customError, setCustomError] = useState<string | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();

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

  function toggleEmail(email: string, checked: boolean) {
    const normalized = normalizeEmail(email);
    if (checked) {
      if (selectedEmails.includes(normalized)) return;
      onChange([...selectedEmails, normalized]);
      return;
    }
    onChange(selectedEmails.filter((e) => e !== normalized));
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
    if (selectedEmails.includes(email)) {
      setCustomError("That recipient is already selected.");
      return;
    }
    onChange([...selectedEmails, email]);
    setCustomEmail("");
    setCustomError(null);
  }

  const triggerLabel =
    selectedEmails.length === 0
      ? "Select admin recipients…"
      : `${selectedEmails.length} recipient${selectedEmails.length === 1 ? "" : "s"} selected`;

  const datalistId = `${listId}-users`;

  return (
    <div ref={rootRef} className={cn("space-y-2", disabled && "pointer-events-none opacity-60")}>
      {selectedEmails.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Selected recipients">
          {selectedEmails.map((email) => (
            <li key={email}>
              <span className="inline-flex max-w-full items-center gap-1 rounded-full border border-ui bg-ui-elevated py-0.5 pl-2.5 pr-1 text-xs text-ui">
                <span className="truncate" title={email}>
                  {labelForEmail(email, adminOptions, allUsers)}
                </span>
                <button
                  type="button"
                  className="rounded-full px-1 text-faint hover:bg-black/10 hover:text-ui dark:hover:bg-white/10"
                  aria-label={`Remove ${email}`}
                  onClick={() => toggleEmail(email, false)}
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
          onClick={() => setOpen((v) => !v)}
          className="flex w-full items-center justify-between gap-2 rounded-[var(--ui-radius)] border border-ui bg-ui-elevated px-3 py-2 text-left text-sm text-ui outline-none transition-colors hover:bg-black/[0.02] focus:border-[var(--ui-primary)] focus:ring-2 focus:ring-brand dark:hover:bg-white/[0.03]"
        >
          <span className={selectedEmails.length === 0 ? "text-muted" : "font-medium"}>{triggerLabel}</span>
          <span className="text-faint" aria-hidden>
            {open ? "▴" : "▾"}
          </span>
        </button>

        {open && (
          <div
            id={listId}
            className="absolute z-20 mt-1 w-full rounded-[var(--ui-radius)] border border-ui bg-ui-surface shadow-lg"
          >
            <div className="max-h-52 overflow-y-auto p-2">
              <p className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wide text-faint">
                Users with admin roles
              </p>
              {adminOptions.length === 0 ? (
                <p className="px-2 py-2 text-xs text-muted">
                  No admin-role users yet.{" "}
                  <Link href="/users" className="link-brand">
                    Assign an admin role
                  </Link>{" "}
                  or add a custom email below.
                </p>
              ) : (
                adminOptions.map((o) => {
                  const checked = selectedEmails.includes(o.email);
                  const roleHint = o.roleNames.length > 0 ? o.roleNames.join(", ") : "Admin role";
                  return (
                    <label
                      key={o.id}
                      className="flex cursor-pointer items-start gap-2 rounded-lg px-2 py-2 text-sm hover:bg-ui-elevated"
                    >
                      <input
                        type="checkbox"
                        className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--ui-primary)]"
                        checked={checked}
                        onChange={(e) => toggleEmail(o.email, e.target.checked)}
                      />
                      <span className="min-w-0">
                        <span className="block font-medium text-ui">{o.label}</span>
                        <span className="block truncate text-xs text-muted">
                          {o.email} · {roleHint}
                        </span>
                      </span>
                    </label>
                  );
                })
              )}
            </div>

            <div className="border-t border-ui p-3">
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-faint">
                Add any user (custom email)
              </p>
              <div className="flex flex-wrap gap-2">
                <div className="min-w-0 flex-1">
                  <Input
                    type="email"
                    list={allUsers.length > 0 ? datalistId : undefined}
                    value={customEmail}
                    placeholder="name@company.com"
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
                      {allUsers.map((u) => (
                        <option key={u.id} value={u.email}>
                          {u.label}
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
                Pick from suggestions or type any email. Custom addresses stay selected even if they are not admin
                users.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
