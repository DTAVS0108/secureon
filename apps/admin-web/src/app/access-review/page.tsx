import { PageHeader } from "@/components/ui/PageHeader";
import { AccessReviewPanel } from "@/components/access/AccessReviewPanel";
import { ChangeRequestsPanel } from "@/components/approvals/ChangeRequestsPanel";
import { PlatformOperatorsPanel } from "@/components/access/PlatformOperatorsPanel";
import { requirePlatformAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

/** Platform operators and the platform-wide privileged access review (platform super admin only). */
export default async function AccessReviewPage() {
  await requirePlatformAccess();
  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="Access review"
        description="Approve high-impact changes, and review who holds platform and console roles, when they last signed in, and whether they use MFA."
      />
      <ChangeRequestsPanel />
      <PlatformOperatorsPanel />
      <AccessReviewPanel />
    </div>
  );
}
