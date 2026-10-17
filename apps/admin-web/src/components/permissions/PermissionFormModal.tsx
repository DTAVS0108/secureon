"use client";

import { useActionState, useState } from "react";
import { cn } from "@/lib/cn";
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
          ? "Change the key or description. Renaming a key affects any application code that checks the old key."
          : "Add a permission your application can check. Keys are unique per application."
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

  const [key, setKey] = useState(permission?.key ?? "");
  const parsed = parsePermissionKey(key);

  function setAction(nextAction: string) {
    const resource = key.split(":")[0]?.trim().toLowerCase() || "resource";
    setKey(`${resource}:${nextAction}`);
  }

  return (
    <form action={formAction} className="pb-2">
      <input type="hidden" name="applicationId" value={applicationId} />
      {permission && <input type="hidden" name="id" value={permission.id} />}
      <FormError state={state} />

      <div className="grid gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <FormSection title="Permission" description="A key your application checks, and what it allows.">
          <FieldRow label="Key" hint="resource:action, lowercase">
            <Input
              name="key"
              value={key}
              onChange={(e) => setKey(e.target.value.toLowerCase())}
              placeholder="user:read"
              required={!permission}
              pattern={KEY_PATTERN}
              disabled={pending}
              className="font-mono text-sm"
              autoComplete="off"
              spellCheck={false}
            />
          </FieldRow>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            {key.length === 0 ? (
              <span className="text-muted">Type a key such as order:refund.</span>
            ) : parsed ? (
              <>
                <span className="rounded-md bg-emerald-500/10 px-2 py-0.5 text-emerald-700 dark:text-emerald-300">
                  Valid
                </span>
                <span className="text-muted">
                  resource <code className="font-mono text-ui">{parsed.resource}</code> · action{" "}
                  <code className="font-mono text-ui">{parsed.action}</code>
                </span>
              </>
            ) : (
              <span className="rounded-md bg-amber-500/10 px-2 py-0.5 text-amber-700 dark:text-amber-300">
                Use resource:action — lowercase letters, digits, - or _, starting with a letter
              </span>
            )}
          </div>
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted">Common actions</p>
            <div className="flex flex-wrap gap-1.5">
              {COMMON_ACTIONS.map((a) => (
                <button
                  key={a}
                  type="button"
                  onClick={() => setAction(a)}
                  disabled={pending}
                  className={cn(
                    "rounded-full border px-2.5 py-0.5 font-mono text-xs transition-colors",
                    parsed?.action === a
                      ? "border-ui-primary bg-brand-muted text-brand"
                      : "border-ui text-muted hover:bg-ui-elevated hover:text-ui",
                  )}
                >
                  {a}
                </button>
              ))}
            </div>
          </div>
          <FieldRow label="Description">
            <Textarea
              name="description"
              defaultValue={permission?.description ?? ""}
              rows={3}
              placeholder="What this permission allows, for the admins who assign it"
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

        <FormSection title="Naming guide">
          <ul className="space-y-2 text-xs leading-relaxed text-muted">
            <li>
              <strong className="text-ui">Resource</strong> is the thing being protected:{" "}
              <code className="font-mono">order</code>, <code className="font-mono">invoice</code>,{" "}
              <code className="font-mono">user</code>.
            </li>
            <li>
              <strong className="text-ui">Action</strong> is what may be done to it:{" "}
              <code className="font-mono">read</code>, <code className="font-mono">refund</code>,{" "}
              <code className="font-mono">export</code>.
            </li>
            <li>Keys are unique per application and appear in the access token, so keep them short and stable.</li>
            <li>After saving, grant the permission to roles on the Roles page or in the matrix on this page.</li>
          </ul>
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted">Examples</p>
            <div className="flex flex-wrap gap-1.5">
              {EXAMPLES.map((example) => (
                <button
                  key={example}
                  type="button"
                  onClick={() => setKey(example)}
                  disabled={pending || Boolean(permission)}
                  className="rounded-md border border-ui px-2 py-0.5 font-mono text-xs text-muted transition-colors hover:bg-ui-elevated hover:text-ui disabled:cursor-default disabled:hover:bg-transparent"
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        </FormSection>
      </div>

      <FormActions
        pending={pending}
        close={close}
        submitLabel={permission ? "Save changes" : "Create permission"}
        summary={parsed ? <code className="font-mono">{parsed.resource}:{parsed.action}</code> : undefined}
        submitDisabled={!permission && !parsed}
        sticky
      />
    </form>
  );
}

const KEY_PATTERN = "[a-z][a-z0-9_\\-]*:[a-z][a-z0-9_\\-]*";
const KEY_REGEX = new RegExp(`^${KEY_PATTERN}$`);
const COMMON_ACTIONS = ["read", "create", "update", "delete", "manage", "export"];
const EXAMPLES = ["order:read", "order:refund", "invoice:export", "user:manage"];

function parsePermissionKey(key: string): { resource: string; action: string } | null {
  const k = key.trim();
  if (!KEY_REGEX.test(k)) return null;
  const [resource, action] = k.split(":");
  return { resource, action };
}
