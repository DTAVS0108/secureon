"use client";

import { useCallback, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { LocalTime } from "@/components/ui/LocalTime";
import { ActionNotice } from "@/components/users/SecuritySection";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import {
  fetchUserAttributes,
  listRoleAssignments,
  listUserIdentities,
  saveUserAttributes,
  setRoleAssignment,
  unlinkUserIdentity,
  type LinkedIdentity,
  type RoleAssignment,
  type SignupField,
} from "@/lib/api/user-profile";

type Notice = { text: string; tone: "success" | "error" } | null;
const errorText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/** The user's profile attributes (sign-up fields, imported values), editable; labelled by the app's sign-up fields. */
export function UserAttributesCard({ userId, applicationId }: { userId: string; applicationId?: string }) {
  const [rows, setRows] = useState<{ key: string; value: string }[] | null>(null);
  const [fields, setFields] = useState<SignupField[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    try {
      const data = await fetchUserAttributes(userId, applicationId);
      setFields(data.fields ?? []);
      const keys = new Set([...(data.fields ?? []).map((f) => f.key), ...Object.keys(data.attributes)]);
      setRows([...keys].map((key) => ({ key, value: data.attributes[key] === undefined ? "" : String(data.attributes[key]) })));
    } catch (e) {
      setNotice({ text: errorText(e, "Could not load attributes."), tone: "error" });
      setRows([]);
    }
  }, [userId, applicationId]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function save() {
    if (!rows) return;
    setBusy(true);
    setNotice(null);
    try {
      const attributes: Record<string, string | boolean> = {};
      for (const r of rows) {
        if (!r.key.trim()) continue;
        const field = fields.find((f) => f.key === r.key);
        attributes[r.key.trim()] = field?.type === "checkbox" ? r.value === "true" : r.value;
      }
      await saveUserAttributes(userId, attributes, applicationId);
      setNotice({ text: "Attributes saved.", tone: "success" });
      await load();
    } catch (e) {
      setNotice({ text: errorText(e, "Save failed."), tone: "error" });
    } finally {
      setBusy(false);
    }
  }

  if (!rows) return <p className="text-xs text-muted">Loading…</p>;
  return (
    <div className="space-y-2">
      {rows.length === 0 && <p className="text-xs text-muted">No attributes.</p>}
      {rows.map((r, i) => {
        const field = fields.find((f) => f.key === r.key);
        return (
          <div key={i} className="grid items-center gap-2 sm:grid-cols-[1fr_1.6fr_auto]">
            {field ? (
              <span className="text-xs text-ui">
                {field.label} <code className="font-mono text-faint">{field.key}</code>
              </span>
            ) : (
              <Input value={r.key} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))} placeholder="key" aria-label="Attribute key" />
            )}
            <Input
              value={r.value}
              onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
              placeholder={field?.type === "checkbox" ? "true or false" : "value (empty removes it)"}
              aria-label={`Value of ${r.key || "attribute"}`}
            />
            <Button variant="ghost" size="sm" onClick={() => setRows(rows.map((x, j) => (j === i ? { ...x, value: "" } : x)))}>
              Clear
            </Button>
          </div>
        );
      })}
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={() => setRows([...rows, { key: "", value: "" }])}>
          Add attribute
        </Button>
        <Button size="sm" disabled={busy} onClick={() => void save()}>
          {busy ? "Saving…" : "Save attributes"}
        </Button>
      </div>
      {notice && <ActionNotice message={notice.text} tone={notice.tone} />}
    </div>
  );
}

/** The Google, SAML… accounts linked to this user; an administrator can unlink them. */
export function UserIdentitiesCard({ userId, applicationId }: { userId: string; applicationId?: string }) {
  const [rows, setRows] = useState<LinkedIdentity[] | null>(null);
  const dialog = useDialog();
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    try {
      setRows(await listUserIdentities(userId, applicationId));
    } catch (e) {
      setNotice({ text: errorText(e, "Could not load linked accounts."), tone: "error" });
      setRows([]);
    }
  }, [userId, applicationId]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function unlink(row: LinkedIdentity) {
    if (
      !(await dialog.confirm({
        title: `Unlink ${row.provider}?`,
        message: `${row.email ?? "No email"}: the user can no longer sign in with it.`,
        confirmLabel: "Unlink",
      }))
    )
      return;
    try {
      await unlinkUserIdentity(userId, row.id, applicationId);
      setNotice({ text: `${row.provider} unlinked.`, tone: "success" });
      await load();
    } catch (e) {
      setNotice({ text: errorText(e, "Could not unlink."), tone: "error" });
    }
  }

  return (
    <div className="space-y-2">
      {rows === null ? (
        <p className="text-xs text-muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="text-xs text-muted">None linked.</p>
      ) : (
        <ul className="space-y-1 text-xs">
          {rows.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2">
              <span>
                <span className="font-medium text-ui">{r.provider}</span> · {r.email ?? "no email"}
                {r.linkedAt && (
                  <span className="text-muted">
                    {" "}· linked <LocalTime value={r.linkedAt} format="date" />
                  </span>
                )}
              </span>
              <Button variant="ghost" size="sm" onClick={() => void unlink(r)}>
                Unlink
              </Button>
            </li>
          ))}
        </ul>
      )}
      {notice && <ActionNotice message={notice.text} tone={notice.tone} />}
    </div>
  );
}

/** The user's roles in this application, each for good or until a date (the role stops counting then). */
export function RoleEndDatesCard({ userId, applicationId }: { userId: string; applicationId: string }) {
  const [rows, setRows] = useState<RoleAssignment[] | null>(null);
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    try {
      setRows(await listRoleAssignments(userId, applicationId));
    } catch (e) {
      setNotice({ text: errorText(e, "Could not load roles."), tone: "error" });
      setRows([]);
    }
  }, [userId, applicationId]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function setEnd(row: RoleAssignment, local: string | null) {
    try {
      await setRoleAssignment(userId, applicationId, row.roleId, local ? new Date(local).toISOString() : null);
      setNotice({ text: local ? `${row.roleName} ends then.` : `${row.roleName} is now for good.`, tone: "success" });
      await load();
    } catch (e) {
      setNotice({ text: errorText(e, "Could not change the role."), tone: "error" });
    }
  }

  return (
    <div className="space-y-2">
      {rows === null ? (
        <p className="text-xs text-muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="text-xs text-muted">No roles in this application.</p>
      ) : (
        <ul className="space-y-2 text-xs">
          {rows.map((r) => (
            <li key={r.roleId} className="flex flex-wrap items-center gap-2">
              <span className="min-w-32 font-medium text-ui">{r.roleName}</span>
              {r.expiresAt ? (
                <span className="text-amber-700 dark:text-amber-300">
                  until <LocalTime value={r.expiresAt} format="datetime" />
                </span>
              ) : (
                <span className="text-muted">for good</span>
              )}
              <input
                type="datetime-local"
                aria-label={`End of ${r.roleName}`}
                className="rounded-md border border-ui bg-ui-elevated px-2 py-1 text-xs text-ui"
                onChange={(e) => e.target.value && void setEnd(r, e.target.value)}
              />
              {r.expiresAt && (
                <Button variant="ghost" size="sm" onClick={() => void setEnd(r, null)}>
                  Make it for good
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {notice && <ActionNotice message={notice.text} tone={notice.tone} />}
    </div>
  );
}
