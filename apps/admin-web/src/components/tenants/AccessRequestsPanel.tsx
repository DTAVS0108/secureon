"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  approveAccessRequest,
  createAccessRequest,
  denyAccessRequest,
  listAccessRequests,
  type AccessRequest,
} from "@/lib/api/access-requests";
import { fetchOperatorProfile } from "@/lib/api/profile";
import type { TenantWorkspaceUser } from "@/lib/api/tenant-workspace";
import type { Application } from "@/lib/types";

function statusTone(status: string): "success" | "warning" | "neutral" | "danger" {
  switch (status.toUpperCase()) {
    case "APPROVED":
      return "success";
    case "PENDING":
      return "warning";
    case "DENIED":
      return "danger";
    default:
      return "neutral";
  }
}

export function AccessRequestsPanel({
  tenantId,
  users,
  applications,
}: {
  tenantId: string;
  users: TenantWorkspaceUser[];
  applications: Application[];
}) {
  const { toast } = useToast();
  const [rows, setRows] = useState<AccessRequest[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [reviewerId, setReviewerId] = useState<string | null>(null);
  const [requesterUserId, setRequesterUserId] = useState("");
  const [targetUserId, setTargetUserId] = useState("");
  const [resourceType, setResourceType] = useState("role");
  const [resourceId, setResourceId] = useState("");
  const [applicationId, setApplicationId] = useState("");
  const [reason, setReason] = useState("");
  const [creating, setCreating] = useState(false);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      setRows(await listAccessRequests(tenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load access requests", "error");
    } finally {
      setLoaded(true);
    }
  }, [tenantId, toast]);

  useDeferredEffect(() => {
    reload();
    void fetchOperatorProfile()
      .then((profile) => setReviewerId(profile.userId))
      .catch(() => {
        /* approve/deny disabled without reviewer id */
      });
  }, [reload]);

  async function handleCreate() {
    if (!requesterUserId || !resourceType.trim() || !resourceId.trim()) return;
    setCreating(true);
    try {
      await createAccessRequest(tenantId, {
        requesterUserId,
        targetUserId: targetUserId || undefined,
        resourceType: resourceType.trim(),
        resourceId: resourceId.trim(),
        applicationId: applicationId || undefined,
        reason: reason.trim() || undefined,
      });
      setResourceId("");
      setReason("");
      setTargetUserId("");
      setApplicationId("");
      toast("Access request created", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleApprove(id: string) {
    if (!reviewerId) {
      toast("Reviewer identity unavailable — sign in as a tenant user", "error");
      return;
    }
    try {
      const approved = await approveAccessRequest(tenantId, id, reviewerId);
      if (approved.grantApplied) {
        toast(`Approved — granted ${approved.grantedRoleName ?? approved.grantedRoleId}`, "success");
      } else {
        toast("Request approved", "success");
      }
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Approve failed", "error");
    }
  }

  async function handleDeny(id: string) {
    if (!reviewerId) {
      toast("Reviewer identity unavailable — sign in as a tenant user", "error");
      return;
    }
    try {
      await denyAccessRequest(tenantId, id, reviewerId);
      toast("Request denied", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Deny failed", "error");
    }
  }

  const needsApplication = resourceType === "application_role" || resourceType === "app_role";

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Access requests"
        description="Request access to tenant or application roles. Approval automatically grants the role."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <FieldRow label="Requester">
            <Select value={requesterUserId} onChange={(e) => setRequesterUserId(e.target.value)} className="w-full">
              <option value="">Select user…</option>
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName || user.email}
                </option>
              ))}
            </Select>
          </FieldRow>
          <FieldRow label="Grant to (optional)">
            <Select value={targetUserId} onChange={(e) => setTargetUserId(e.target.value)} className="w-full">
              <option value="">Same as requester</option>
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName || user.email}
                </option>
              ))}
            </Select>
          </FieldRow>
          <FieldRow label="Resource type">
            <Select value={resourceType} onChange={(e) => setResourceType(e.target.value)} className="w-full">
              <option value="role">role (tenant first, then app)</option>
              <option value="tenant_role">tenant_role</option>
              <option value="application_role">application_role</option>
            </Select>
          </FieldRow>
          {needsApplication && (
            <FieldRow label="Application">
              <Select value={applicationId} onChange={(e) => setApplicationId(e.target.value)} className="w-full">
                <option value="">Select application…</option>
                {applications.map((app) => (
                  <option key={app.id} value={app.id}>
                    {app.name}
                  </option>
                ))}
              </Select>
            </FieldRow>
          )}
          <FieldRow label="Role name or UUID">
            <Input
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              placeholder="Support Agent or role uuid"
            />
          </FieldRow>
          <FieldRow label="Reason">
            <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Optional justification" />
          </FieldRow>
        </div>
        <Button
          onClick={() => void handleCreate()}
          disabled={creating || !requesterUserId || !resourceId.trim() || (needsApplication && !applicationId)}
        >
          {creating ? "Submitting…" : "Submit request"}
        </Button>

        {rows.length === 0 ? (
          <p className="text-sm text-muted">No access requests yet.</p>
        ) : (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {rows.map((row) => (
              <li key={row.id} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ui">
                    {row.resourceType}: {row.resourceId}
                  </p>
                  {row.reason && <p className="text-xs text-muted">{row.reason}</p>}
                  {row.status === "APPROVED" && row.grantApplied && (
                    <p className="text-xs text-muted">
                      Granted {row.grantedRoleName ?? row.grantedRoleId} → user {row.grantedUserId?.slice(0, 8)}…
                    </p>
                  )}
                  <Badge tone={statusTone(row.status)} className="mt-1">
                    {row.status}
                  </Badge>
                </div>
                {row.status === "PENDING" && (
                  <div className="flex gap-2">
                    <Button variant="secondary" size="sm" onClick={() => void handleApprove(row.id)}>
                      Approve
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void handleDeny(row.id)}>
                      Deny
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
