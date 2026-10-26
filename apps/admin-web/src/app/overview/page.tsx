import { PageHeader } from "@/components/ui/PageHeader";
import { OverviewPanel } from "@/components/overview/OverviewPanel";
import { requirePlatformAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

/** Platform home (platform super admin): sign-ins, active users, two-factor use and security events. */
export default async function OverviewPage() {
  await requirePlatformAccess();
  return (
    <div className="w-full min-w-0">
      <PageHeader title="Overview" description="How the platform is doing: sign-ins, active users, two-factor use and recent security events." />
      <OverviewPanel />
    </div>
  );
}
