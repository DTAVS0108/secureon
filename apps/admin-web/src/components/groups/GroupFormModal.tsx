"use client";

import { useActionState, useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input, Textarea } from "@/components/ui/Field";
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

  const roleFields = useMemo(
    () => [...selectedRoles].map((id) => ({ key: "roleIds", value: id })),
    [selectedRoles],
  );
  const memberFields = useMemo(
    () => [...selectedMembers].map((id) => ({ key: "memberUserIds", value: id })),
    [selectedMembers],
  );

  return (
    <form action={formAction} className="space-y-5">
      {editing && group && <input type="hidden" name="id" value={group.id} />}
      <input type="hidden" name="tenantId" value={tenantId} />
      <input type="hidden" name="applicationId" value={applicationId} />

      <FieldRow label="Name">
        <Input name="name" defaultValue={group?.name ?? ""} required placeholder="Engineering" />
      </FieldRow>
      <FieldRow label="Description">
        <Textarea
          name="description"
          defaultValue={group?.description ?? ""}
          placeholder="Optional description for admins"
          rows={2}
        />
      </FieldRow>

      <div>
        <p className="text-sm font-medium text-ui">Roles in this group</p>
        <p className="mt-0.5 text-xs text-muted">Members receive all selected roles.</p>
        {roles.length === 0 ? (
          <p className="mt-2 text-sm text-muted">No roles available. Create roles first.</p>
        ) : (
          <ul className="mt-3 max-h-40 space-y-1 overflow-y-auto rounded-lg border border-ui p-2">
            {roles.map((role) => (
              <li key={role.id}>
                <label className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 hover:bg-ui-elevated">
                  <input
                    type="checkbox"
                    checked={selectedRoles.has(role.id)}
                    onChange={() => {
                      setSelectedRoles((prev) => {
                        const next = new Set(prev);
                        if (next.has(role.id)) next.delete(role.id);
                        else next.add(role.id);
                        return next;
                      });
                    }}
                    className="h-4 w-4 accent-[var(--ui-primary,#4f46e5)]"
                  />
                  <span className="text-sm text-ui">{role.name}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <p className="text-sm font-medium text-ui">Members</p>
        <p className="mt-0.5 text-xs text-muted">Users in this tenant who belong to the application.</p>
        {users.length === 0 ? (
          <p className="mt-2 text-sm text-muted">No users in this application yet.</p>
        ) : (
          <ul className="mt-3 max-h-40 space-y-1 overflow-y-auto rounded-lg border border-ui p-2">
            {users.map((user) => (
              <li key={user.id}>
                <label className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 hover:bg-ui-elevated">
                  <input
                    type="checkbox"
                    checked={selectedMembers.has(user.id)}
                    onChange={() => {
                      setSelectedMembers((prev) => {
                        const next = new Set(prev);
                        if (next.has(user.id)) next.delete(user.id);
                        else next.add(user.id);
                        return next;
                      });
                    }}
                    className="h-4 w-4 accent-[var(--ui-primary,#4f46e5)]"
                  />
                  <span className="text-sm text-ui">{user.firstName || user.username || user.email}</span>
                  <span className="text-xs text-muted">{user.email}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>

      {roleFields.map((f) => (
        <input key={f.value} type="hidden" name={f.key} value={f.value} />
      ))}
      {memberFields.map((f) => (
        <input key={f.value} type="hidden" name={f.key} value={f.value} />
      ))}

      <FormError state={state} />
      <FormActions pending={pending} close={close} submitLabel={editing ? "Save group" : "Create group"} />
    </form>
  );
}
