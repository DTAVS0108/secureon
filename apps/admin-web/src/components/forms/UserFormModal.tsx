"use client";

import { useActionState, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { FormSection, FormFieldGrid } from "@/components/forms/FormSection";
import { userCreateAction, userUpdateAction, type FormState } from "@/lib/actions";
import { BooleanCheckbox, CheckboxGroup, FormActions, FormError, useCloseOnSuccess } from "./form-utils";
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
      width="lg"
      title={editing ? "Edit user" : "Invite user"}
      description={
        editing
          ? "Update profile, status, and role assignments."
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

  const roleOptions = roles.map((r) => ({
    value: r.id,
    label: r.name,
    hint: r.description,
  }));

  return (
    <form action={formAction} className="space-y-5 pb-2">
      {user && <input type="hidden" name="id" value={user.id} />}
      {applicationId && <input type="hidden" name="applicationId" value={applicationId} />}
      <FormError state={state} />

      {lockToApp && !user && (
        <p className="rounded-lg border border-brand/20 bg-brand-muted/30 px-3 py-2.5 text-xs text-muted">
          User will be added to this application automatically after invite.
        </p>
      )}

      <FormSection title="Profile">
        <FormFieldGrid>
          <FieldRow label="First name">
            <Input name="firstName" defaultValue={user?.firstName} placeholder="Sarah" required disabled={pending} />
          </FieldRow>
          <FieldRow label="Last name">
            <Input name="lastName" defaultValue={user?.lastName} placeholder="Chen" required disabled={pending} />
          </FieldRow>
        </FormFieldGrid>
        <FieldRow label="Email">
          <Input
            name="email"
            type="email"
            defaultValue={user?.email}
            placeholder="you@company.com"
            required
            disabled={pending}
          />
        </FieldRow>
        <FormFieldGrid>
          <FieldRow label="Username" hint="optional">
            <Input name="username" defaultValue={user?.username} placeholder="schen" disabled={pending} />
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
        <FieldRow label="Status">
          <Select name="status" defaultValue={user?.status ?? defaultStatus} disabled={pending}>
            <option value="invited">Invited (pending password)</option>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
            <option value="disabled">Disabled</option>
          </Select>
        </FieldRow>
        {user && (
          <>
            <input type="hidden" name="emailVerified" value={emailVerified ? "true" : "false"} />
            <BooleanCheckbox
              label="Email verified"
              hint="Marks the address verified immediately. Users can also verify via the link sent from Security → Resend verification email; uncheck to require that flow again."
              checked={emailVerified}
              onCheckedChange={setEmailVerified}
              disabled={pending}
            />
          </>
        )}
      </FormSection>

      {applicationId && (
        <FormSection title="Roles" description="RBAC roles for this tenant/application.">
          <CheckboxGroup name="roleIds" options={roleOptions} selected={user?.roleIds ?? []} />
        </FormSection>
      )}

      <FormActions
        pending={pending}
        close={close}
        submitLabel={user ? "Save changes" : "Invite user"}
        sticky
      />
    </form>
  );
}
