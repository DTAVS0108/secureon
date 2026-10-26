import { PageHeader } from "@/components/ui/PageHeader";
import { OverviewPanel } from "@/components/overview/OverviewPanel";
import { requireTenantGovernanceAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

/** Tenant home (tenant super admin or platform super admin): the same overview for one tenant. */
export default async function TenantOverviewPage({ params }: { params: Promise<{ id: string }> }) {
  await requireTenantGovernanceAccess();
  const { id } = await params;
  return (
    <div className="w-full min-w-0">
      <PageHeader title="Overview" description="This tenant's sign-ins, active users, two-factor use and recent security events." />
      <OverviewPanel tenantId={id} />
    </div>
  );
}
