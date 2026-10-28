"use client";

import { useActionState, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input } from "@/components/ui/Field";
import { FormSection } from "@/components/forms/FormSection";
import { SelectableList } from "@/components/forms/SelectableList";
import { FormActions, FormError, useCloseOnSuccess } from "@/components/forms/form-utils";
import { groupCreateAction, groupUpdateAction, type FormState } from "@/lib/actions";
import type { RbacGroup, RbacGroupDetail, Role, User } from "@/lib/types";

const initial: FormState = { ok: false };

export function GroupFormModal({
  group,
  roles,
  users,
  tenantId,
  applicationId,
  onCreated,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  group?: RbacGroup | RbacGroupDetail;
  roles: Role[];
  users: User[];
  tenantId: string;
  applicationId: string;
  onCreated?: (groupId: string) => void;
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const editing = Boolean(group);
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="lg"
      title={editing ? "Edit group" : "New group"}
      description={
        editing
          ? "Update the group name, assigned roles, and members."
          : "Create a group and assign one or more roles. Members inherit all group roles."
      }
    >
      {(close) => (
        <GroupForm
          group={group}
          roles={roles}
          users={users}
          tenantId={tenantId}
          applicationId={applicationId}
          onCreated={onCreated}
          close={close}
        />
      )}
    </Modal>
  );
}

function GroupForm({
  group,
  roles,
  users,
  tenantId,
  applicationId,
  onCreated,
  close,
}: {
  group?: RbacGroup | RbacGroupDetail;
  roles: Role[];
  users: User[];
  tenantId: string;
  applicationId: string;
  onCreated?: (groupId: string) => void;
  close: () => void;
}) {
  const editing = Boolean(group);
  const action = editing ? groupUpdateAction : groupCreateAction;
  const [state, formAction, pending] = useActionState(action, initial);
  const [selectedRoles, setSelectedRoles] = useState<Set<string>>(
    () => new Set(group?.roleIds ?? []),
  );
  const [selectedMembers, setSelectedMembers] = useState<Set<string>>(
    () => new Set(group?.memberUserIds ?? []),
  );

  useDeferredEffect(() => {
    setSelectedRoles(new Set(group?.roleIds ?? []));
    setSelectedMembers(new Set(group?.memberUserIds ?? []));
  }, [group]);

  useCloseOnSuccess(state, close, () => {
    if (state.createdGroupId && onCreated) onCreated(state.createdGroupId);
  });

  const roleCount = selectedRoles.size;
  const memberCount = selectedMembers.size;

  return (
    <form action={formAction} className="pb-2">
      {editing && group && <input type="hidden" name="id" value={group.id} />}
      <input type="hidden" name="tenantId" value={tenantId} />
      <input type="hidden" name="applicationId" value={applicationId} />
      <FormError state={state} />

      <FormSection title="Details">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
          <FieldRow label="Name">
            <Input name="name" defaultValue={group?.name ?? ""} required placeholder="Group name" disabled={pending} />
          </FieldRow>
          <FieldRow label="Description" hint="optional">
            <Input
              name="description"
              defaultValue={group?.description ?? ""}
              placeholder="Who belongs here and why, for other admins"
              disabled={pending}
            />
          </FieldRow>
        </div>
      </FormSection>

      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <SelectableList
          title="Roles in this group"
          description="Every member receives all of these roles."
          items={roles.map((r) => ({ id: r.id, label: r.name, detail: r.description }))}
          selected={[...selectedRoles]}
          onChange={(ids) => setSelectedRoles(new Set(ids))}
          name="roleIds"
          searchPlaceholder="Search roles…"
          emptyText="No roles yet. Create roles first on the Roles page."
          disabled={pending}
        />
        <SelectableList
          title="Members"
          description="People in this application who get the group's roles."
          items={users.map((u) => ({
            id: u.id,
            label: `${u.firstName} ${u.lastName}`.trim() || u.username || u.email,
            detail: u.email,
          }))}
          selected={[...selectedMembers]}
          onChange={(ids) => setSelectedMembers(new Set(ids))}
          name="memberUserIds"
          searchPlaceholder="Search people…"
          emptyText="No users in this application yet."
          disabled={pending}
        />
      </div>

      <FormActions
        pending={pending}
        close={close}
        submitLabel={editing ? "Save group" : "Create group"}
        summary={`${roleCount} ${roleCount === 1 ? "role" : "roles"} · ${memberCount} ${memberCount === 1 ? "member" : "members"}`}
        sticky
      />
    </form>
  );
}
