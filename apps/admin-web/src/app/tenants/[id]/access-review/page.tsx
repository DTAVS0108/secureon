import { PageHeader } from "@/components/ui/PageHeader";
import { AccessReviewPanel } from "@/components/access/AccessReviewPanel";
import { assertTenantScope, requireTenantGovernanceAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

/** Privileged access review for one tenant (platform super admin, or that tenant's super admin). */
export default async function TenantAccessReviewPage({ params }: { params: Promise<{ id: string }> }) {
  const ctx = await requireTenantGovernanceAccess();
  const { id } = await params;
  assertTenantScope(ctx, id);
  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="Access review"
        description="Console roles in this tenant, when each person last signed in, and whether they use MFA."
      />
      <AccessReviewPanel tenantId={id} />
    </div>
  );
}
