import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { AuditTable } from "@/app/audit/AuditTable";
import { resolveApplicationMeta } from "@/lib/api/app-workspace";
import { listAuditEvents } from "@/lib/api/audit";

export const dynamic = "force-dynamic";

export default async function AppAuditPage({
  params,
}: {
  params: Promise<{ applicationId: string }>;
}) {
  const { applicationId } = await params;
  const app = await resolveApplicationMeta(applicationId);
  if (!app) notFound();
  // Always available (who may see it is decided by console access, not by a feature flag).

  let events: Awaited<ReturnType<typeof listAuditEvents>> = [];
  try {
    events = await listAuditEvents(applicationId, app.tenantId);
  } catch {
    /* show empty table */
  }

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href={`/app/${applicationId}/audit`} className="hover:underline">Audit log</Link>}
        title="Audit log"
        description={`${app.name} · ${app.tenantName}`}
      />
      <AuditTable events={events} />
    </div>
  );
}
