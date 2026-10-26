import { redirect } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { AuditSearchPanel } from "@/components/audit/AuditSearchPanel";
import { loadAdminContextSafe } from "@/lib/api/app-workspace";
import { buildAppPath } from "@/lib/app-routes";

export const dynamic = "force-dynamic";

/** Platform super admins: every tenant's audit log. Others: their application's audit log. */
export default async function AuditPage() {
  const ctx = await loadAdminContextSafe();
  if (ctx.platformSuperAdmin) {
    return (
      <div className="w-full min-w-0">
        <PageHeader
          title="Audit log"
          description="Every tenant and application: search, filter and export. Retention and streaming to a SIEM are under Platform settings → Logs & streaming."
        />
        <AuditSearchPanel />
      </div>
    );
  }
  if (ctx.applications.length > 0) {
    redirect(buildAppPath(ctx.applications[0].id, "audit"));
  }
  redirect("/app");
}
