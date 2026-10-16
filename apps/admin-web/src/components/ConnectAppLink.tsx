"use client";

import Link from "next/link";
import { cn } from "@/lib/cn";

/** "Connect an app" for tenant operators who manage at least one application's OAuth clients. */
export function ConnectAppLink({ pathname }: { pathname: string }) {
  const active = pathname === "/connect" || pathname.startsWith("/connect/");
  return (
    <div className="shrink-0 border-t border-ui py-3">
      <Link
        href="/connect"
        className={cn(
          "block rounded-lg px-2.5 py-2 text-sm transition-colors",
          active ? "bg-brand font-medium text-on-brand" : "text-soft hover:bg-ui-elevated",
        )}
      >
        Connect an app
      </Link>
    </div>
  );
}
