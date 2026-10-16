"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  addPlatformOperator,
  listPlatformOperators,
  removePlatformOperator,
  type PlatformOperator,
} from "@/lib/api/access-governance";
import { LocalTime } from "@/components/ui/LocalTime";

/** Named platform super admins (accounts in the reserved "platform" tenant). */
export function PlatformOperatorsPanel() {
  const { toast } = useToast();
  const [rows, setRows] = useState<PlatformOperator[] | null>(null);
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);

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
      await addPlatformOperator({ email: email.trim(), firstName, lastName });
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
        description="Named people with full platform access. They sign in with tenant slug “platform”, their email, password and an authenticator app. The configured emergency account is for incidents only."
      />
      <Table>
        <THead>
          <tr>
            <TH>Person</TH>
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
              <TD colSpan={5} className="py-8 text-center text-sm text-faint">
                No named platform operators yet. Add one, then stop using the emergency account.
              </TD>
            </TR>
          )}
        </TBody>
      </Table>
      <div className="grid gap-3 border-t border-ui px-5 py-4 sm:grid-cols-4 sm:items-end">
        <FieldRow label="Email">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" />
        </FieldRow>
        <FieldRow label="First name">
          <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} />
        </FieldRow>
        <FieldRow label="Last name">
          <Input value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </FieldRow>
        <Button onClick={() => void add()} disabled={busy === "add"}>
          {busy === "add" ? "Adding…" : "Add platform operator"}
        </Button>
      </div>
    </Card>
  );
}
