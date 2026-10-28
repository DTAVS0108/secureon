"use client";

import { useActionState } from "react";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { applicationProductCreateAction, type FormState } from "@/lib/actions";
import { CheckboxGroup, FormActions, FormError, useCloseOnSuccess } from "./form-utils";
import type { Tenant } from "@/lib/types";

const initial: FormState = { ok: false };

export function ApplicationProductFormModal({
  tenants,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  tenants: Tenant[];
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="lg"
      title="Register application"
      description="Creates an application product and provisions a dedicated database schema for isolated IAM data."
    >
      {(close) => <ApplicationProductForm tenants={tenants} close={close} />}
    </Modal>
  );
}

function ApplicationProductForm({ tenants, close }: { tenants: Tenant[]; close: () => void }) {
  const [state, formAction, pending] = useActionState(applicationProductCreateAction, initial);
  useCloseOnSuccess(state, close);

  return (
    <form action={formAction} className="space-y-4">
      <FormError state={state} />
      <div className="grid grid-cols-2 gap-4">
        <FieldRow label="Display name">
          <Input name="name" placeholder="Application name" required />
        </FieldRow>
        <FieldRow label="Tenant">
          <Select name="tenantId" defaultValue={tenants[0]?.id} disabled={tenants.length === 0} required>
            {tenants.length === 0 && <option value="">No tenants available</option>}
            {tenants.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </Select>
        </FieldRow>
      </div>
      <FieldRow label="Slug" hint="Used for the application's schema name">
        <Input name="slug" placeholder="Slug" />
      </FieldRow>
      <FieldRow label="Description" hint="Optional">
        <Textarea name="description" rows={2} placeholder="Description" />
      </FieldRow>
      <FieldRow label="Status">
        <Select name="status" defaultValue="active">
          <option value="active">Active</option>
          <option value="disabled">Disabled</option>
          <option value="suspended">Suspended</option>
        </Select>
      </FieldRow>
      <FormActions
        pending={pending}
        close={close}
        submitLabel="Register application"
        submitDisabled={tenants.length === 0}
      />
    </form>
  );
}
