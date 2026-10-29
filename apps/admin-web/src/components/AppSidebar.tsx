"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { appNav } from "./nav";
import { PlatformAdminNav } from "./PlatformAdminNav";
import { BrandMark } from "./BrandMark";
import { isTenantSuperAdmin } from "@/lib/operator-access";
import { APP_NAME, APP_TAGLINE } from "@/lib/config";
import { cn } from "@/lib/cn";
import { buildAppPath } from "@/lib/app-routes";
import { useAdminContext } from "./AdminContextProvider";
import { useAppFlags } from "@/hooks/useAppFlags";
import { APP_FLAG } from "@/lib/app-feature-flags";
import { ConnectAppLink } from "./ConnectAppLink";
import { canAccessSection } from "@/lib/auth/permissions";

export function AppSidebar({
  applicationId,
  superAdmin,
}: {
  applicationId: string;
  superAdmin: boolean;
}) {
  const pathname = usePathname();
  const ctx = useAdminContext();
  const app = ctx.applications.find((a) => a.id === applicationId);
  const flags = useAppFlags();
  const items = appNav(applicationId).filter((item) => {
    const section = item.href.split("/").pop() ?? "";
    if (section === "sessions" && !flags.on(APP_FLAG.sessions)) return false;
    return canAccessSection(superAdmin, app, section);
  });
  const tenantSuper = isTenantSuperAdmin(ctx);
  const showPlatformAdmin = superAdmin || tenantSuper;
  const canConnect = !superAdmin && ctx.applications.some((a) => a.canManageClients);

  return (
    <aside className="sticky top-0 flex h-screen w-64 shrink-0 flex-col border-r border-ui bg-ui-surface px-3 py-5">
      <Link href={buildAppPath(applicationId, "users")} className="mb-5 flex items-center gap-2.5 px-2">
        <BrandMark />
        <div>
          <p className="text-sm font-semibold leading-tight text-ui">{APP_NAME}</p>
          <p className="text-xs text-muted">{APP_TAGLINE}</p>
        </div>
      </Link>

      <p className="mb-2 px-2 text-[10px] font-semibold uppercase tracking-wider text-faint">
        Application
      </p>
      <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto">
        {items.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "rounded-lg px-2.5 py-2 text-sm transition-colors",
                active ? "bg-brand font-medium text-on-brand" : "text-soft hover:bg-ui-elevated",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      {canConnect && ctx.connectAppEnabled !== false && <ConnectAppLink pathname={pathname} />}

      {showPlatformAdmin && (
        <div className="shrink-0 border-t border-ui pt-4">
          <p className="mb-2 px-2 text-[10px] font-semibold uppercase tracking-wider text-faint">
            Platform admin
          </p>
          <PlatformAdminNav
            pathname={pathname}
            variant={superAdmin ? "full" : "tenant-only"}
            tenantId={ctx.tenantId}
          />
        </div>
      )}
    </aside>
  );
}
