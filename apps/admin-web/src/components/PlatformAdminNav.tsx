"use client";

import Link from "next/link";
import { SUPER_ADMIN_NAV, TENANT_NAV_GROUP, tenantNavGroupForOperator } from "./nav";
import { SidebarNavGroup } from "./SidebarNavGroup";
import { SidebarNavItem } from "./SidebarNavItem";
import { cn } from "@/lib/cn";
import { useAdminContext } from "./AdminContextProvider";
import type { SidebarNavGroupConfig } from "./nav";

export type PlatformAdminNavVariant = "full" | "tenant-only";

/** Platform admin links. Tenant super-admins see only the Tenant group. */
export function PlatformAdminNav({
  pathname,
  variant = "full",
  tenantId,
}: {
  pathname: string;
  variant?: PlatformAdminNavVariant;
  tenantId?: string | null;
}) {
  const ctx = useAdminContext();
  const tenantGroup: SidebarNavGroupConfig =
    variant === "tenant-only" && tenantId
      ? tenantNavGroupForOperator(tenantId)
      : TENANT_NAV_GROUP;

  if (variant === "tenant-only") {
    return (
      <nav className="flex flex-col gap-0.5">
        <SidebarNavGroup
          label={tenantGroup.label}
          items={tenantGroup.children}
          pathname={pathname}
        />
      </nav>
    );
  }

  // Connect an app and Configuration as code show only when turned on in Platform settings → Feature flags.
  const items = SUPER_ADMIN_NAV.filter(
    (item) =>
      (item.href !== "/config" || ctx.configAsCodeEnabled) &&
      (item.href !== "/connect" || ctx.connectAppEnabled !== false),
  );
  // Fixed order: Connect an app (when on), the Tenant group, then OAuth clients and the rest.
  const first = items.find((item) => item.href === "/connect");
  const rest = items.filter((item) => item.href !== "/connect");

  return (
    <nav className="flex flex-col gap-0.5">
      {first && (
        <Link
          href={first.href}
          className={cn(
            "rounded-lg px-2.5 py-2 text-sm transition-colors",
            pathname === first.href || pathname.startsWith(`${first.href}/`)
              ? "bg-brand font-medium text-on-brand"
              : "text-soft hover:bg-ui-elevated",
          )}
        >
          {first.label}
        </Link>
      )}
      <SidebarNavGroup
        label={tenantGroup.label}
        items={tenantGroup.children}
        pathname={pathname}
      />
      {rest.map((item) => (
        <SidebarNavItem
          key={item.href}
          item={item}
          active={pathname === item.href || pathname.startsWith(`${item.href}/`)}
        />
      ))}
    </nav>
  );
}
