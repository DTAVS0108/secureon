"use client";

import { useActionState, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { FormSection, FormFieldGrid } from "@/components/forms/FormSection";
import { userCreateAction, userUpdateAction, type FormState } from "@/lib/actions";
import { FormActions, FormError, useCloseOnSuccess } from "./form-utils";
import { cn } from "@/lib/cn";
import { SelectableList } from "./SelectableList";
import type { Role, Tenant, User } from "@/lib/types";

const initial: FormState = { ok: false };

export function UserFormModal({
  user,
  tenants,
  roles,
  tenantId,
  applicationId,
  lockToApp = false,
  defaultStatus = "invited",
  onCreated,
  onUserUpdated,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  user?: User;
  tenants: Tenant[];
  roles: Role[];
  tenantId?: string;
  applicationId?: string;
  lockToApp?: boolean;
  defaultStatus?: "invited" | "active" | "suspended" | "disabled";
  onCreated?: (userId: string) => void;
  onUserUpdated?: () => void | Promise<void>;
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const editing = Boolean(user);
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="2xl"
      title={editing ? "Edit user" : "Invite user"}
      description={
        editing
          ? "Update the profile, account status and roles in this application."
          : lockToApp
            ? "Creates the account and grants access to this application. A set-password email is sent."
            : "Create or invite a new user account."
      }
    >
      {(close) => (
        <UserForm
          user={user}
          tenants={tenants}
          roles={roles}
          tenantId={tenantId}
          applicationId={applicationId}
          lockToApp={lockToApp}
          defaultStatus={defaultStatus}
          onCreated={onCreated}
          onUserUpdated={onUserUpdated}
          close={close}
        />
      )}
    </Modal>
  );
}

function UserForm({
  user,
  tenants,
  roles,
  tenantId,
  applicationId,
  lockToApp,
  defaultStatus,
  onCreated,
  onUserUpdated,
  close,
}: {
  user?: User;
  tenants: Tenant[];
  roles: Role[];
  tenantId?: string;
  applicationId?: string;
  lockToApp: boolean;
  defaultStatus: "invited" | "active" | "suspended" | "disabled";
  onCreated?: (userId: string) => void;
  onUserUpdated?: () => void | Promise<void>;
  close: () => void;
}) {
  const action = user ? userUpdateAction : userCreateAction;
  const [state, formAction, pending] = useActionState(action, initial);
  const [emailVerified, setEmailVerified] = useState(user?.emailVerified ?? false);
  const effectiveTenantId = lockToApp && tenantId ? tenantId : user?.tenantId ?? tenantId ?? "";

  useDeferredEffect(() => {
    if (user) setEmailVerified(user.emailVerified);
  }, [user?.id, user?.emailVerified]);

  useCloseOnSuccess(state, close, async (s) => {
    if (s.createdUserId) {
      onCreated?.(s.createdUserId);
      try {
        sessionStorage.setItem("users:lastCreated", s.createdUserId);
      } catch {
        /* ignore */
      }
    } else if (user) {
      await onUserUpdated?.();
    }
  });

  return (
    <form action={formAction} className="pb-2">
      {user && <input type="hidden" name="id" value={user.id} />}
      {applicationId && <input type="hidden" name="applicationId" value={applicationId} />}
      <FormError state={state} />

      {lockToApp && !user && (
        <p className="mb-5 rounded-lg border border-brand/20 bg-brand-muted/30 px-3 py-2.5 text-xs text-muted">
          The user is added to this application and receives an email to set their password.
        </p>
      )}

      <div className={applicationId ? "grid gap-5 lg:grid-cols-2" : "grid gap-5"}>
        <div className="space-y-5">
          <FormSection title="Profile">
            <FormFieldGrid>
              <FieldRow label="First name">
                <Input name="firstName" defaultValue={user?.firstName} placeholder="First name" required disabled={pending} />
              </FieldRow>
              <FieldRow label="Last name">
                <Input name="lastName" defaultValue={user?.lastName} placeholder="Last name" required disabled={pending} />
              </FieldRow>
            </FormFieldGrid>
            <FieldRow label="Email" hint="sign-in address">
              <Input
                name="email"
                type="email"
                defaultValue={user?.email}
                placeholder="Email address"
                required
                disabled={pending}
              />
            </FieldRow>
            <FormFieldGrid>
              <FieldRow label="Username" hint="optional">
                <Input name="username" defaultValue={user?.username} placeholder="Username" disabled={pending} />
              </FieldRow>
              {!lockToApp && (
                <FieldRow label="Tenant">
                  <Select
                    name="tenantId"
                    defaultValue={effectiveTenantId}
                    disabled={Boolean(user) || pending}
                    required
                  >
                    {!user && <option value="">Select tenant…</option>}
                    {tenants.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </Select>
                </FieldRow>
              )}
            </FormFieldGrid>
            {lockToApp && !user && effectiveTenantId && (
              <input type="hidden" name="tenantId" value={effectiveTenantId} />
            )}
          </FormSection>

          <FormSection title="Account status">
            <StatusPicker defaultValue={user?.status ?? defaultStatus} disabled={pending} />
            {user && (
              <>
                <input type="hidden" name="emailVerified" value={emailVerified ? "true" : "false"} />
                <ToggleRow
                  label="Email verified"
                  description="Unverified users cannot sign in with a password. Turn off to make them confirm their address again (Security → Resend verification email)."
                  checked={emailVerified}
                  onChange={setEmailVerified}
                  disabled={pending}
                />
              </>
            )}
          </FormSection>
        </div>

        {applicationId && (
          <RolePicker roles={roles} initialSelected={user?.roleIds ?? []} disabled={pending} />
        )}
      </div>

      <FormActions
        pending={pending}
        close={close}
        submitLabel={user ? "Save changes" : "Invite user"}
        sticky
      />
    </form>
  );
}

const STATUS_OPTIONS = [
  { value: "invited", label: "Invited", description: "Must set a password from the email link before signing in." },
  { value: "active", label: "Active", description: "Can sign in." },
  { value: "suspended", label: "Suspended", description: "Blocked for now; reactivate any time." },
  { value: "disabled", label: "Disabled", description: "Switched off; cannot sign in." },
] as const;

/** Account status as option cards (posts `status`). */
function StatusPicker({ defaultValue, disabled }: { defaultValue: string; disabled: boolean }) {
  const [value, setValue] = useState(defaultValue);
  return (
    <fieldset>
      <legend className="mb-2 text-xs font-medium text-muted">Status</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {STATUS_OPTIONS.map((option) => (
          <label
            key={option.value}
            className={cn(
              "flex cursor-pointer gap-2.5 rounded-lg border px-3 py-2.5 transition-colors",
              value === option.value ? "border-ui-primary bg-brand-muted/40" : "border-ui hover:bg-ui-elevated",
              disabled && "cursor-not-allowed opacity-60",
            )}
          >
            <input
              type="radio"
              name="status"
              value={option.value}
              checked={value === option.value}
              onChange={() => setValue(option.value)}
              disabled={disabled}
              className="mt-0.5 h-4 w-4 accent-[var(--ui-primary)]"
            />
            <span className="min-w-0">
              <span className="block text-sm font-medium text-ui">{option.label}</span>
              <span className="block text-xs leading-snug text-muted">{option.description}</span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-4 rounded-lg border border-ui px-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-medium text-ui">{label}</p>
        <p className="mt-0.5 text-xs leading-snug text-muted">{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          "relative mt-0.5 inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors disabled:opacity-60",
          checked ? "bg-[var(--ui-primary)]" : "bg-black/20 dark:bg-white/20",
        )}
      >
        <span
          className={cn(
            "inline-block h-4 w-4 rounded-full bg-white shadow transition-transform",
            checked ? "translate-x-4" : "translate-x-0.5",
          )}
        />
      </button>
    </div>
  );
}

/** The application's roles; selected ones are posted as `roleIds`. */
function RolePicker({
  roles,
  initialSelected,
  disabled,
}: {
  roles: Role[];
  initialSelected: string[];
  disabled: boolean;
}) {
  const [selected, setSelected] = useState<string[]>(() =>
    initialSelected.filter((id) => roles.some((r) => r.id === id)),
  );
  return (
    <SelectableList
      title="Roles"
      description="What this user can do in the application."
      items={roles.map((r) => ({ id: r.id, label: r.name, detail: r.description }))}
      selected={selected}
      onChange={setSelected}
      name="roleIds"
      searchPlaceholder="Search roles…"
      emptyText="This application has no roles yet. Create them on the Roles page."
      disabled={disabled}
    />
  );
}
