"use client";

import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { tenantCreateAction, tenantUpdateAction, type FormState } from "@/lib/actions";
import type { Tenant } from "@/lib/types";
import { useActionState } from "react";
import { FormActions, FormError, useCloseOnSuccess } from "./form-utils";

const initial: FormState = { ok: false };

export function TenantFormModal({
  tenant,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  tenant?: Tenant;
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const editing = Boolean(tenant);
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      title={editing ? "Edit tenant" : "New tenant"}
      description={editing ? "Update organization details." : "Create a new isolated organization."}
    >
      {(close) => <TenantForm tenant={tenant} close={close} />}
    </Modal>
  );
}

function TenantForm({ tenant, close }: { tenant?: Tenant; close: () => void }) {
  const action = tenant ? tenantUpdateAction : tenantCreateAction;
  const [state, formAction, pending] = useActionState(action, initial);
  useCloseOnSuccess(state, close);

  return (
    <form action={formAction} className="space-y-4">
      {tenant && <input type="hidden" name="id" value={tenant.id} />}
      <FormError state={state} />
      <FieldRow label="Name">
        <Input name="name" defaultValue={tenant?.name} placeholder="Your organization" required />
      </FieldRow>
      <FieldRow
        label="Slug"
        hint={tenant?.system ? "reserved — the platform tenant's slug cannot change" : "optional — auto-generated"}
      >
        <Input name="slug" defaultValue={tenant?.slug} placeholder="your-org" readOnly={tenant?.system} />
      </FieldRow>
      <div className="grid grid-cols-2 gap-4">
        <FieldRow label="Plan">
          {/* A disabled select is not submitted, so the plan goes in a hidden field. */}
          <input type="hidden" name="plan" value={tenant?.plan ?? "enterprise"} />
          <Select disabled defaultValue={tenant?.plan ?? "enterprise"}>
            <option value="free">Free</option>
            <option value="team">Team</option>
            <option value="enterprise">Enterprise</option>
          </Select>
        </FieldRow>
        <FieldRow label="Status">
          {/* A disabled select is not submitted; the hidden field keeps the platform tenant active. */}
          {tenant?.system && <input type="hidden" name="status" value="active" />}
          <Select name="status" defaultValue={tenant?.status ?? "active"} disabled={tenant?.system}>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
          </Select>
        </FieldRow>
      </div>
      <FormActions pending={pending} close={close} submitLabel={tenant ? "Save changes" : "Create tenant"} />
    </form>
  );
}
