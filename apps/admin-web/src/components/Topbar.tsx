"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { initials } from "@/lib/format";
import { ApplicationSelector } from "./ApplicationSelector";
import { OperatorProfileModal } from "./profile/OperatorProfileModal";
import { useAdminContext } from "./AdminContextProvider";
import type { ApplicationContextItem } from "@/lib/api/context";
import { withBasePath } from "@/lib/config";

export function Topbar({
  applications,
  activeApplicationId,
  inAppWorkspace,
  onApplicationChange,
  registryHasClients = true,
}: {
  applications: ApplicationContextItem[];
  activeApplicationId?: string;
  inAppWorkspace: boolean;
  onApplicationChange?: (applicationId: string) => void;
  registryHasClients?: boolean;
}) {
  const router = useRouter();
  const [profileOpen, setProfileOpen] = useState(false);
  const ctx = useAdminContext();
  const displayName = ctx.displayName || ctx.principal || "Operator";
  const subtitle =
    ctx.platformSuperAdmin
      ? "Platform super admin"
      : ctx.operatorTier === "tenant_super"
        ? "Tenant super admin"
        : ctx.operatorTier === "tenant"
          ? "Tenant admin"
          : "Application admin";

  async function signOut() {
    await fetch(withBasePath("/api/auth/logout"), { method: "POST" });
    router.replace("/login?reason=signed-out");
    router.refresh();
  }

  return (
    <header className="sticky top-0 z-10 flex h-14 items-center justify-between gap-4 border-b border-ui bg-[var(--ui-surface)]/85 px-8 backdrop-blur">
      <div className="flex min-w-0 flex-1 items-center gap-4">
        {registryHasClients ? (
          <ApplicationSelector
            applications={applications}
            currentApplicationId={activeApplicationId}
            inAppWorkspace={inAppWorkspace}
            onApplicationChange={onApplicationChange}
          />
        ) : (
          <span className="text-sm text-muted">Application console unavailable — register an OAuth client first</span>
        )}
        <div className="relative hidden max-w-xs flex-1 md:block">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-faint">
            ⌕
          </span>
          <input
            type="search"
            placeholder="Search…"
            className="h-9 w-full rounded-[var(--ui-radius)] border border-ui bg-ui-elevated pl-9 pr-3 text-sm text-[var(--ui-text)] outline-none placeholder-ui focus:border-[var(--ui-primary)] focus:ring-2 focus:ring-brand"
          />
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-3">
        <button
          type="button"
          onClick={() => void signOut()}
          className="hidden rounded-lg border border-ui px-2.5 py-1.5 text-xs text-soft hover:bg-ui-elevated sm:block"
        >
          Sign out
        </button>
        <button
          type="button"
          onClick={() => setProfileOpen(true)}
          className="flex items-center gap-3 rounded-lg px-1 py-1 transition-colors hover:bg-ui-elevated"
          title="My profile"
        >
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-muted text-xs font-semibold text-brand">
            {initials(displayName)}
          </div>
          <div className="hidden leading-tight sm:block">
            <p className="text-xs font-medium text-[var(--ui-text)]">{displayName}</p>
            <p className="text-[11px] text-faint">{subtitle}</p>
          </div>
        </button>
        <OperatorProfileModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      </div>
    </header>
  );
}
