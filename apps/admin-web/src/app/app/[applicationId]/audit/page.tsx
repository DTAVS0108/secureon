import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { AuditSearchPanel } from "@/components/audit/AuditSearchPanel";
import { resolveApplicationMeta } from "@/lib/api/app-workspace";

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

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href={`/app/${applicationId}/audit`} className="hover:underline">Audit log</Link>}
        title="Audit log"
        description={`${app.name} · ${app.tenantName}`}
      />
      <AuditSearchPanel applicationId={applicationId} />
    </div>
  );
}
