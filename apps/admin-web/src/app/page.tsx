import { redirect } from "next/navigation";
import { loadAdminContextSafe } from "@/lib/api/app-workspace";
import { buildAppPath } from "@/lib/app-routes";
import { hasOAuthClients } from "@/lib/oauth-client-registry";

export default async function HomePage() {
  const ctx = await loadAdminContextSafe();
  // Platform super admins start on the overview, like other identity consoles.
  if (ctx.platformSuperAdmin) redirect("/overview");
  if (!hasOAuthClients(ctx)) {
    if (ctx.operatorTier === "tenant" || ctx.operatorTier === "tenant_super") redirect("/app");
    redirect("/login");
  }
  if (
    (ctx.operatorTier === "tenant" || ctx.operatorTier === "tenant_super") &&
    ctx.applications.length > 0
  ) {
    redirect(buildAppPath(ctx.applications[0].id, "users"));
  }
  if (ctx.applications.length > 0) {
    redirect(buildAppPath(ctx.applications[0].id, "users"));
  }
  if (ctx.platformSuperAdmin) {
    redirect("/applications");
  }
  redirect("/app");
}
