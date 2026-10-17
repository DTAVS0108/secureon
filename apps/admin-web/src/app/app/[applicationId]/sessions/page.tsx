import Link from "next/link";
import { notFound } from "next/navigation";
import { APP_FLAG, appFlagOn } from "@/lib/app-feature-flags";
import { PageHeader } from "@/components/ui/PageHeader";
import { loadAdminContextSafe, resolveApplicationMeta } from "@/lib/api/app-workspace";
import { listLoginEvents } from "@/lib/api/sessions";
import { SessionsTable } from "@/app/sessions/SessionsTable";

export const dynamic = "force-dynamic";

export default async function AppSessionsPage({
  params,
}: {
  params: Promise<{ applicationId: string }>;
}) {
  const { applicationId } = await params;
  const app = await resolveApplicationMeta(applicationId);
  if (!app) notFound();
  // Settings → Feature flags → Sessions: the page is not found while the flag is off.
  if (!appFlagOn((await loadAdminContextSafe()).consoleFlags, APP_FLAG.sessions)) notFound();

  let sessions: Awaited<ReturnType<typeof listLoginEvents>> = [];
  try {
    sessions = await listLoginEvents({ applicationId, tenantId: app.tenantId });
  } catch {
    /* show empty */
  }

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href={`/app/${applicationId}/sessions`} className="hover:underline">Sessions</Link>}
        title="Sessions"
        description={`Sign-in history for ${app.name}`}
      />
      <SessionsTable applicationId={applicationId} events={sessions} />
    </div>
  );
}
