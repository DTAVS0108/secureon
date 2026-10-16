"use client";

import { useActionState } from "react";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input, Textarea } from "@/components/ui/Field";
import { FormSection } from "@/components/forms/FormSection";
import {
  permissionCreateAction,
  permissionUpdateAction,
  type FormState,
} from "@/lib/actions";
import { FormActions, FormError, useCloseOnSuccess } from "@/components/forms/form-utils";
import type { Permission } from "@/lib/types";

const initial: FormState = { ok: false };

export function PermissionFormModal({
  permission,
  applicationId,
  onCreated,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  permission?: Permission;
  applicationId: string;
  onCreated?: (permissionId: string) => void;
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const editing = Boolean(permission);
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="lg"
      title={editing ? "Edit permission" : "New permission"}
      description={
        editing
          ? "Updates the permission row in PostgreSQL."
          : "Adds a catalog entry (unique key per application)."
      }
    >
      {(close) => (
        <PermissionForm
          permission={permission}
          applicationId={applicationId}
          onCreated={onCreated}
          close={close}
        />
      )}
    </Modal>
  );
}

function PermissionForm({
  permission,
  applicationId,
  onCreated,
  close,
}: {
  permission?: Permission;
  applicationId: string;
  onCreated?: (permissionId: string) => void;
  close: () => void;
}) {
  const action = permission ? permissionUpdateAction : permissionCreateAction;
  const [state, formAction, pending] = useActionState(action, initial);

  useCloseOnSuccess(state, close, (s) => {
    if (s.createdPermissionId) {
      onCreated?.(s.createdPermissionId);
      try {
        sessionStorage.setItem("permissions:lastCreated", s.createdPermissionId);
      } catch {
        /* ignore */
      }
    }
  });

  return (
    <form action={formAction} className="space-y-5 pb-2">
      <input type="hidden" name="applicationId" value={applicationId} />
      {permission && <input type="hidden" name="id" value={permission.id} />}
      <FormError state={state} />

      <FormSection
        title="Permission row"
        description="Maps to permission.key and permission.description columns."
      >
        <FieldRow label="Key" hint="Format: resource:action (lowercase)">
          <Input
            name="key"
            defaultValue={permission?.key ?? ""}
            placeholder="user:read"
            required={!permission}
            pattern="[a-z][a-z0-9_\-]*:[a-z][a-z0-9_\-]*"
            disabled={pending}
            className="font-mono text-sm"
          />
        </FieldRow>
        <FieldRow label="Description">
          <Textarea
            name="description"
            defaultValue={permission?.description ?? ""}
            rows={3}
            placeholder="What this permission allows"
            disabled={pending}
          />
        </FieldRow>
        {permission && (
          <div className="rounded-lg border border-ui bg-ui-elevated/40 px-3 py-2">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-faint">id</p>
            <p className="mt-0.5 break-all font-mono text-xs text-muted">{permission.id}</p>
          </div>
        )}
      </FormSection>

      <FormActions
        pending={pending}
        close={close}
        submitLabel={permission ? "Save changes" : "Create permission"}
        sticky
      />
    </form>
  );
}
