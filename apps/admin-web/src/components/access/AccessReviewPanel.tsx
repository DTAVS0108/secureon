"use client";

import { useEffect, useMemo, useState } from "react";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  completeAccessReview,
  loadAccessReview,
  type AccessReviewEntry,
  type AccessReviewFlag,
} from "@/lib/api/access-governance";
import { LocalTime } from "@/components/ui/LocalTime";

const ROLE_LABELS: Record<AccessReviewEntry["role"], string> = {
  PLATFORM_OPERATOR: "Platform super admin",
  TENANT_SUPER_ADMIN: "Tenant super admin",
  TENANT_ADMIN: "Tenant admin",
  APPLICATION_ADMIN: "Application admin",
};

const FLAG_LABELS: Record<AccessReviewFlag, { label: string; tone: BadgeTone }> = {
  ACCOUNT_NOT_ACTIVE: { label: "Account not active", tone: "danger" },
  NEVER_SIGNED_IN: { label: "Never signed in", tone: "warning" },
  UNUSED_90_DAYS: { label: "Unused 90+ days", tone: "warning" },
  NO_MFA: { label: "No MFA", tone: "danger" },
};

function toCsv(rows: AccessReviewEntry[]) {
  const header = [
    "email", "name", "tenant", "role", "application", "granted_at", "granted_by",
    "last_sign_in", "mfa", "account_status", "flags",
  ];
  const escape = (v: string) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);
  const lines = rows.map((r) =>
    [
      r.email, r.displayName ?? "", r.tenantSlug, ROLE_LABELS[r.role], r.applicationName ?? "",
      r.grantedAt ?? "", r.grantedBy ?? "", r.lastLoginAt ?? "", r.mfaEnrolled ? "yes" : "no",
      r.accountStatus, r.flags.join(" "),
    ].map((v) => escape(String(v))).join(","),
  );
  return [header.join(","), ...lines].join("\n");
}

/**
 * Privileged access review: who holds console or platform roles, last use, MFA and flags.
 * Without `tenantId` it covers the whole platform (platform super admins only).
 */
export function AccessReviewPanel({ tenantId }: { tenantId?: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<AccessReviewEntry[] | null>(null);
  const [onlyFlagged, setOnlyFlagged] = useState(false);
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadAccessReview(tenantId)
      .then((next) => {
        if (!cancelled) setRows(next);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        toast(e instanceof Error ? e.message : "Could not load the access review", "error");
        setRows([]);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, toast]);

  const visible = useMemo(
    () => (rows ?? []).filter((r) => !onlyFlagged || r.flags.length > 0),
    [rows, onlyFlagged],
  );
  const flaggedCount = (rows ?? []).filter((r) => r.flags.length > 0).length;

  function download() {
    const blob = new Blob([toCsv(rows ?? [])], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `access-review-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function complete() {
    setSaving(true);
    try {
      await completeAccessReview(tenantId, { note, entriesReviewed: rows?.length ?? 0 });
      toast("Review recorded in the audit log", "success");
      setNote("");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not record the review", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Access review"
        description={
          rows === null
            ? "Loading…"
            : `${rows.length} privileged assignments, ${flaggedCount} flagged. Remove access that is no longer needed, then record the review.`
        }
        action={
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" onClick={() => setOnlyFlagged((v) => !v)}>
              {onlyFlagged ? "Show all" : "Flagged only"}
            </Button>
            <Button size="sm" variant="secondary" onClick={download} disabled={!rows?.length}>
              Export CSV
            </Button>
          </div>
        }
      />
      <div className="overflow-x-auto">
        <Table>
          <THead>
            <tr>
              <TH>Person</TH>
              <TH>Role</TH>
              {!tenantId && <TH>Tenant</TH>}
              <TH>Scope</TH>
              <TH>Granted</TH>
              <TH>Last sign-in</TH>
              <TH>Flags</TH>
            </tr>
          </THead>
          <TBody>
            {visible.map((r) => (
              <TR key={`${r.role}-${r.assignmentId ?? r.userId}`}>
                <TD>
                  <p className="font-medium">{r.displayName || r.email}</p>
                  <p className="text-xs text-faint">{r.email}</p>
                </TD>
                <TD>{ROLE_LABELS[r.role]}</TD>
                {!tenantId && <TD className="text-sm text-soft">{r.tenantName}</TD>}
                <TD className="text-sm text-soft">
                  {r.role === "PLATFORM_OPERATOR"
                    ? "Whole platform"
                    : r.role === "TENANT_SUPER_ADMIN"
                      ? "All applications"
                      : (r.applicationName ?? "—")}
                </TD>
                <TD className="text-sm text-soft">
                  <LocalTime value={r.grantedAt} />
                  {r.grantedBy && <p className="text-xs text-faint">by {r.grantedBy}</p>}
                </TD>
                <TD className="text-sm text-soft"><LocalTime value={r.lastLoginAt} /></TD>
                <TD>
                  <div className="flex flex-wrap gap-1">
                    {r.flags.length === 0 ? (
                      <Badge tone="success">OK</Badge>
                    ) : (
                      r.flags.map((f) => (
                        <Badge key={f} tone={FLAG_LABELS[f]?.tone ?? "neutral"}>
                          {FLAG_LABELS[f]?.label ?? f}
                        </Badge>
                      ))
                    )}
                  </div>
                </TD>
              </TR>
            ))}
            {rows !== null && visible.length === 0 && (
              <TR>
                <TD colSpan={tenantId ? 6 : 7} className="py-8 text-center text-sm text-faint">
                  {onlyFlagged ? "Nothing flagged." : "No privileged access granted."}
                </TD>
              </TR>
            )}
          </TBody>
        </Table>
      </div>
      <div className="flex flex-col gap-2 border-t border-ui px-5 py-4 sm:flex-row sm:items-center">
        <Input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Review note (optional), e.g. Q4 review, removed 2 stale admins"
          className="flex-1"
          maxLength={500}
        />
        <Button onClick={() => void complete()} disabled={saving || rows === null}>
          {saving ? "Recording…" : "Mark review complete"}
        </Button>
      </div>
    </Card>
  );
}
