import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApplicationLogViewer } from "@/app/logs/ApplicationLogViewer";
import { resolveApplicationMeta } from "@/lib/api/app-workspace";

export const dynamic = "force-dynamic";

export default async function AppLogsPage({
  params,
  searchParams,
}: {
  params: Promise<{ applicationId: string }>;
  searchParams: Promise<{ sessionId?: string; requestId?: string }>;
}) {
  const { applicationId } = await params;
  const { sessionId, requestId } = await searchParams;
  const app = await resolveApplicationMeta(applicationId);
  if (!app) notFound();

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href={`/app/${applicationId}/logs`} className="hover:underline">Application logs</Link>}
        title="Application logs"
        description={`${app.name} · ${app.tenantName}`}
      />
      <ApplicationLogViewer
        key={`${sessionId ?? ""}|${requestId ?? ""}`}
        applicationId={applicationId}
        tenantId={app.tenantId}
        initialSessionId={sessionId}
        initialRequestId={requestId}
      />
    </div>
  );
}
