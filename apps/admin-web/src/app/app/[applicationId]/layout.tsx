import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { loadAdminContextSafe } from "@/lib/api/app-workspace";
import { appSectionFromPath, buildAppPath } from "@/lib/app-routes";
import { hasOAuthClients } from "@/lib/oauth-client-registry";

export default async function AppWorkspaceGuardLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ applicationId: string }>;
}) {
  const ctx = await loadAdminContextSafe();
  if (!hasOAuthClients(ctx)) {
    redirect("/app");
  }
  const { applicationId } = await params;
  if (!ctx.applications.some((a) => a.id === applicationId)) {
    // A deleted application, or one this operator cannot open (old bookmark, remembered last app):
    // open the same section of the first application they can open instead of a 404.
    const first = ctx.applications[0];
    if (!first) redirect("/app");
    const pathname = (await headers()).get("x-pathname") ?? "";
    redirect(buildAppPath(first.id, appSectionFromPath(pathname)));
  }
  return children;
}
