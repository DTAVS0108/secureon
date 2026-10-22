import Link from "next/link";
import { PageHeader } from "@/components/ui/PageHeader";
import { ButtonLink } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { StatCard } from "@/components/ui/StatCard";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { TenantFormModal } from "@/components/forms/TenantFormModal";
import { TenantDetailPanels } from "@/components/tenants/TenantDetailPanels";
import { tenantDeleteAction } from "@/lib/actions";
import { getTenant } from "@/lib/api/tenants";
import { listApplications } from "@/lib/api/applications";
import { listRoles } from "@/lib/api/roles";
import { formatDate } from "@/lib/format";
import { planTone, statusTone } from "@/lib/status";
import { fetchGovernanceTenantWorkspace } from "@/lib/api/tenant-workspace-server";
import {
  assertTenantScope,
  requireTenantGovernanceAccess,
} from "@/lib/platform-access";
import { isTenantSuperAdmin } from "@/lib/operator-access";
import { applicationsFromWorkspace, tenantFromWorkspace } from "@/lib/tenant-governance";
import type { Application, Tenant } from "@/lib/types";

export default async function TenantDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const ctx = await requireTenantGovernanceAccess();
  const tenantSuper = isTenantSuperAdmin(ctx);
  const { id } = await params;
  assertTenantScope(ctx, id);

  const workspace = await fetchGovernanceTenantWorkspace(id, tenantSuper);

  let tenant: Tenant | null = tenantSuper ? tenantFromWorkspace(workspace) : await getTenant(id);
  if (!tenant) {
    tenant = tenantFromWorkspace(workspace);
  }

  const apps: Application[] = tenantSuper
    ? (applicationsFromWorkspace(workspace) as Application[])
    : await listApplications(tenant.id);

  const defaultAppId = workspace.applications[0]?.id ?? apps[0]?.id;
  const roles = defaultAppId ? await listRoles({ applicationId: defaultAppId }) : [];

  const description = tenant.createdAt
    ? `@${tenant.slug} · created ${formatDate(tenant.createdAt)}`
    : `@${tenant.slug}`;

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href="/tenants" className="hover:underline">Tenants</Link>}
        title={tenant.name}
        description={description}
        actions={
          <>
            <Badge tone={planTone(tenant.plan)} className="capitalize">{tenant.plan}</Badge>
            <Badge tone={statusTone(tenant.status)} dot className="capitalize">{tenant.status}</Badge>
            {!tenantSuper && (
              <>
                <ButtonLink href="/applications" variant="ghost" size="sm">
                  OAuth clients
                </ButtonLink>
                <ButtonLink href={`/tenants/${tenant.id}/two-step`} variant="ghost" size="sm">
                  Two-step verification
                </ButtonLink>
                <TenantFormModal tenant={tenant} triggerLabel="Edit" triggerVariant="secondary" />
                {!tenant.system && (
                  <ConfirmDialog
                    action={tenantDeleteAction}
                    id={tenant.id}
                    triggerLabel="Delete"
                    triggerVariant="danger"
                    title={`Delete ${tenant.name}?`}
                    message="This removes the tenant and all associated configuration. This action cannot be undone."
                  />
                )}
              </>
            )}
          </>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Users" value={tenant.userCount.toLocaleString()} />
        <StatCard label="Applications" value={tenant.appCount} />
        <StatCard label="Plan" value={tenant.plan} />
        <StatCard label="Status" value={tenant.status} />
      </div>

      <TenantDetailPanels
        initialWorkspace={workspace}
        tenant={tenant}
        roles={roles}
        applications={apps}
        useOperatorWorkspace={tenantSuper}
      />
    </div>
  );
}
