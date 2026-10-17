"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { PLATFORM_NAV, SUPER_ADMIN_NAV, TENANT_OPERATOR_NAV } from "./nav";
import { PlatformAdminNav } from "./PlatformAdminNav";
import { SidebarNavItem } from "./SidebarNavItem";
import { useAdminContext } from "./AdminContextProvider";
import { ConnectAppLink } from "./ConnectAppLink";
import { isTenantSuperAdmin } from "@/lib/operator-access";
import { APP_NAME, APP_TAGLINE } from "@/lib/config";
import { buildAppPath } from "@/lib/app-routes";
import type { ApplicationContextItem } from "@/lib/api/context";
import { hasOAuthClients } from "@/lib/oauth-client-registry";

/** Fallback sidebar when no applications are loaded yet. */
export function PlatformSidebar({
  superAdmin,
  applications,
}: {
  superAdmin: boolean;
  applications: ApplicationContextItem[];
}) {
  const pathname = usePathname();
  const ctx = useAdminContext();
  const registryHasClients = hasOAuthClients(ctx);
  const tenantSuper = isTenantSuperAdmin(ctx);
  const showPlatformAdmin = superAdmin || tenantSuper;
  const isTenantOperator =
    (ctx.operatorTier === "tenant" || ctx.operatorTier === "tenant_super") && !superAdmin;
  const navSource = isTenantOperator ? TENANT_OPERATOR_NAV : PLATFORM_NAV;
  const canConnect = !superAdmin && ctx.applications.some((a) => a.canManageClients);
  const superAdminHrefs = new Set(SUPER_ADMIN_NAV.map((i) => i.href));
  const items = navSource.filter((i) => {
    if (i.href === "/app" && !registryHasClients) return false;
    return (superAdmin || !i.superAdminOnly) && !(superAdmin && superAdminHrefs.has(i.href));
  });
  const defaultAppHref = !registryHasClients
    ? superAdmin
      ? "/applications"
      : "/app"
    : applications.length > 0
      ? buildAppPath(applications[0].id, "users")
      : isTenantOperator
        ? "/app"
        : "/applications";

  return (
    <aside className="sticky top-0 flex h-screen w-64 shrink-0 flex-col border-r border-ui bg-ui-surface px-3 py-5">
      <Link href={defaultAppHref} className="mb-6 flex items-center gap-2.5 px-2">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand text-on-brand text-sm font-bold shadow-sm">
          S1
        </div>
        <div>
          <p className="text-sm font-semibold leading-tight text-ui">{APP_NAME}</p>
          <p className="text-xs text-muted">{APP_TAGLINE}</p>
        </div>
      </Link>
      <nav className="flex flex-1 flex-col gap-0.5">
        {items.map((item) => {
          const href = item.href === "/app" ? defaultAppHref : item.href;
          return (
            <SidebarNavItem
              key={item.href}
              item={item}
              href={href}
              active={
                item.href === "/app"
                  ? pathname.startsWith("/app/")
                  : pathname === item.href || pathname.startsWith(`${item.href}/`)
              }
            />
          );
        })}
        {canConnect && ctx.connectAppEnabled !== false && <ConnectAppLink pathname={pathname} />}
        {showPlatformAdmin && (
          <div className="mt-3 border-t border-ui pt-3">
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
      </nav>
    </aside>
  );
}
