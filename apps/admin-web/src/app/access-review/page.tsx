import { PageHeader } from "@/components/ui/PageHeader";
import { AccessReviewPanel } from "@/components/access/AccessReviewPanel";
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
        description="Who holds platform and console roles, when they last signed in, and whether they use MFA."
      />
      <PlatformOperatorsPanel />
      <AccessReviewPanel />
    </div>
  );
}
