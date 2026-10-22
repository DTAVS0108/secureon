"use client";

import { usePathname } from "next/navigation";
import { AdminContextProvider } from "./AdminContextProvider";
import { ConsoleShell } from "./ConsoleShell";
import { StepUpProvider } from "./StepUpProvider";
import type { AdminContext } from "@/lib/api/context";

export function ConsoleProviders({
  children,
  adminContext,
}: {
  children: React.ReactNode;
  adminContext: AdminContext;
}) {
  const pathname = usePathname();
  const isLogin = pathname === "/login";
  const isConfluence =
    pathname === "/confluence" || pathname.startsWith("/confluence/");

  if (isLogin) {
    return <>{children}</>;
  }

  if (isConfluence) {
    return (
      <AdminContextProvider value={adminContext}>
        <div className="min-h-screen text-ui">{children}</div>
      </AdminContextProvider>
    );
  }

  return (
    <AdminContextProvider value={adminContext}>
      <ConsoleShell
        superAdmin={adminContext.platformSuperAdmin}
        tenantSuperAdmin={
          adminContext.operatorTier === "tenant_super" && !adminContext.platformSuperAdmin
        }
        applications={adminContext.applications}
      >
        {children}
      </ConsoleShell>
      <StepUpProvider />
    </AdminContextProvider>
  );
}
