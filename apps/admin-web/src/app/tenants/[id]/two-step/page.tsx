import { PageHeader } from "@/components/ui/PageHeader";
import { MfaPolicySettingsPanel } from "@/components/settings/MfaPolicySettingsPanel";
import { assertTenantScope, requireTenantGovernanceAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

/** Tenant defaults for two-step verification (platform super admin, or that tenant's super admin). */
export default async function TenantTwoStepPage({ params }: { params: Promise<{ id: string }> }) {
  const ctx = await requireTenantGovernanceAccess();
  const { id } = await params;
  assertTenantScope(ctx, id);
  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="Two Factor Authentication"
        description="Defaults for every application of this tenant: options, whether it is required, reminders and the grace period."
      />
      <MfaPolicySettingsPanel tenantId={id} />
    </div>
  );
}
