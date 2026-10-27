"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  addPlatformOperator,
  changePlatformOperatorRole,
  listPlatformOperators,
  removePlatformOperator,
  type PlatformOperator,
  type PlatformRole,
} from "@/lib/api/access-governance";

const ROLE_LABEL: Record<PlatformRole, string> = { SUPER_ADMIN: "Super admin", READ_ONLY: "Read-only" };
import { LocalTime } from "@/components/ui/LocalTime";

/** Named platform super admins (accounts in the reserved "platform" tenant). */
export function PlatformOperatorsPanel() {
  const { toast } = useToast();
  const [rows, setRows] = useState<PlatformOperator[] | null>(null);
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [role, setRole] = useState<PlatformRole>("SUPER_ADMIN");
  const [busy, setBusy] = useState<string | null>(null);

  async function changeRole(userId: string, next: PlatformRole) {
    setBusy(userId);
    try {
      await changePlatformOperatorRole(userId, next);
      toast(`Role changed to ${ROLE_LABEL[next]}`, "success");
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not change the role", "error");
    } finally {
      setBusy(null);
    }
  }

  const refresh = useCallback(async () => {
    try {
      setRows(await listPlatformOperators());
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load platform operators", "error");
      setRows([]);
    }
  }, [toast]);

  useEffect(() => {
    let cancelled = false;
    listPlatformOperators()
      .then((next) => {
        if (!cancelled) setRows(next);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        toast(e instanceof Error ? e.message : "Could not load platform operators", "error");
        setRows([]);
      });
    return () => {
      cancelled = true;
    };
  }, [toast]);

  async function add() {
    if (!email.includes("@")) {
      toast("Enter a valid email", "error");
      return;
    }
    setBusy("add");
    try {
      await addPlatformOperator({ email: email.trim(), firstName, lastName, role });
      toast("Added. They get an email to set a password, then set up MFA at first sign-in.", "success");
      setEmail("");
      setFirstName("");
      setLastName("");
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not add operator", "error");
    } finally {
      setBusy(null);
    }
  }

  async function remove(userId: string) {
    setBusy(userId);
    try {
      await removePlatformOperator(userId);
      toast("Platform operator role removed", "success");
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not remove operator", "error");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="Platform operators"
        description="Named people with platform access: super admins change anything; read-only operators (auditors, support) see everything and change nothing. They sign in with tenant slug “platform”, their email, password and an authenticator app. The configured emergency account is for incidents only."
      />
      <Table>
        <THead>
          <tr>
            <TH>Person</TH>
            <TH>Role</TH>
            <TH>Status</TH>
            <TH>Added</TH>
            <TH>Last sign-in</TH>
            <TH />
          </tr>
        </THead>
        <TBody>
          {(rows ?? []).map((r) => (
            <TR key={r.userId}>
              <TD>
                <p className="font-medium">{r.displayName || r.email}</p>
                <p className="text-xs text-faint">{r.email}</p>
              </TD>
              <TD>
                <Select
                  value={r.role ?? "SUPER_ADMIN"}
                  disabled={busy === r.userId}
                  onChange={(e) => void changeRole(r.userId, e.target.value as PlatformRole)}
                  aria-label={`Role of ${r.email}`}
                >
                  <option value="SUPER_ADMIN">{ROLE_LABEL.SUPER_ADMIN}</option>
                  <option value="READ_ONLY">{ROLE_LABEL.READ_ONLY}</option>
                </Select>
              </TD>
              <TD>
                <Badge tone={r.status === "ACTIVE" ? "success" : "warning"}>{r.status}</Badge>
              </TD>
              <TD className="text-sm text-soft">
                <LocalTime value={r.grantedAt} />
                {r.grantedBy && <p className="text-xs text-faint">by {r.grantedBy}</p>}
              </TD>
              <TD className="text-sm text-soft">
                {r.lastLoginAt ? <LocalTime value={r.lastLoginAt} format="datetime" /> : "Never"}
              </TD>
              <TD>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={busy === r.userId}
                  onClick={() => void remove(r.userId)}
                >
                  Remove
                </Button>
              </TD>
            </TR>
          ))}
          {rows !== null && rows.length === 0 && (
            <TR>
              <TD colSpan={6} className="py-8 text-center text-sm text-faint">
                No named platform operators yet. Add one, then stop using the emergency account.
              </TD>
            </TR>
          )}
        </TBody>
      </Table>
      <div className="grid gap-3 border-t border-ui px-5 py-4 sm:grid-cols-5 sm:items-end">
        <FieldRow label="Email">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" />
        </FieldRow>
        <FieldRow label="First name">
          <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} />
        </FieldRow>
        <FieldRow label="Last name">
          <Input value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </FieldRow>
        <FieldRow label="Role">
          <Select value={role} onChange={(e) => setRole(e.target.value as PlatformRole)}>
            <option value="SUPER_ADMIN">{ROLE_LABEL.SUPER_ADMIN}</option>
            <option value="READ_ONLY">{ROLE_LABEL.READ_ONLY}</option>
          </Select>
        </FieldRow>
        <Button onClick={() => void add()} disabled={busy === "add"}>
          {busy === "add" ? "Adding…" : "Add platform operator"}
        </Button>
      </div>
    </Card>
  );
}
