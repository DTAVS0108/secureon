"use client";

import { useCallback, useEffect, useState } from "react";
import { FederationProvidersPanel } from "@/components/settings/FederationProvidersPanel";
import { AccessRequestsPanel } from "@/components/tenants/AccessRequestsPanel";
import { ServiceAccountsPanel } from "@/components/tenants/ServiceAccountsPanel";
import { WebhooksPanel } from "@/components/tenants/WebhooksPanel";
import { DeletionRequestsPanel } from "@/components/tenants/DeletionRequestsPanel";
import { TenantWorkspacePanel } from "@/components/tenant/TenantWorkspacePanel";
import { AdminConsoleAccessPanel } from "@/components/tenants/AdminConsoleAccessPanel";
import { TenantIssuerPanel } from "@/components/tenants/TenantIssuerPanel";
import type { ConsoleAccessAssignment } from "@/lib/api/admin-console-access";
import {
  fetchTenantWorkspace,
  normalizeTenantWorkspaceUser,
  type TenantWorkspace,
} from "@/lib/api/tenant-workspace";
import type { Application, Role, Tenant } from "@/lib/types";

function consoleAccessFrom(workspace: TenantWorkspace): ConsoleAccessAssignment[] {
  return workspace.consoleAccess ?? [];
}

export function TenantDetailPanels({
  initialWorkspace,
  tenant,
  roles,
  applications,
  useOperatorWorkspace = false,
}: {
  initialWorkspace: TenantWorkspace;
  tenant: Tenant;
  roles: Role[];
  applications: Application[];
  /** Tenant super-admin: use operator workspace API (no platform tenantId query). */
  useOperatorWorkspace?: boolean;
}) {
  const [workspace, setWorkspace] = useState(initialWorkspace);
  const [assignments, setAssignments] = useState(() => consoleAccessFrom(initialWorkspace));

  const applyWorkspace = useCallback((next: TenantWorkspace) => {
    if (!Array.isArray(next.users) || !Array.isArray(next.applications)) return;
    const normalized = {
      ...next,
      users: next.users.map((u) => normalizeTenantWorkspaceUser(u)),
      consoleAccess: next.consoleAccess ?? [],
    };
    setWorkspace(normalized);
    setAssignments(consoleAccessFrom(normalized));
  }, []);

  // Server render may omit console access; always refetch once on mount.
  useEffect(() => {
    let cancelled = false;
    void fetchTenantWorkspace(useOperatorWorkspace ? undefined : tenant.id)
      .then((next) => {
        if (!cancelled) applyWorkspace(next);
      })
      .catch(() => {
        /* keep server-provided initial state */
      });
    return () => {
      cancelled = true;
    };
  }, [tenant.id, useOperatorWorkspace, applyWorkspace]);

  const refreshAll = useCallback(async () => {
    const next = await fetchTenantWorkspace(useOperatorWorkspace ? undefined : tenant.id);
    applyWorkspace(next);
  }, [tenant.id, useOperatorWorkspace, applyWorkspace]);

  return (
    <>
      <TenantWorkspacePanel
        workspace={workspace}
        tenant={tenant}
        roles={roles}
        manageTenantId={tenant.id}
        useOperatorWorkspace={useOperatorWorkspace}
        consoleAssignments={assignments}
        onMutated={refreshAll}
      />
      <AdminConsoleAccessPanel
        tenantId={tenant.id}
        applications={applications}
        assignments={assignments}
        onMutated={refreshAll}
      />
      <FederationProvidersPanel tenantId={tenant.id} />
      <TenantIssuerPanel tenantId={tenant.id} />
      <AccessRequestsPanel tenantId={tenant.id} users={workspace.users} applications={applications} />
      <ServiceAccountsPanel tenantId={tenant.id} />
      <WebhooksPanel tenantId={tenant.id} />
      <DeletionRequestsPanel tenantId={tenant.id} />
    </>
  );
}
