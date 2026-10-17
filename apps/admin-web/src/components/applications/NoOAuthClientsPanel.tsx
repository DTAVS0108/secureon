"use client";

import { ButtonLink } from "@/components/ui/Button";
import { useAdminContext } from "@/components/AdminContextProvider";
import { Card } from "@/components/ui/Card";
import type { NoOAuthClientsVariant } from "@/lib/oauth-client-registry";
import Link from "next/link";
import { usePathname } from "next/navigation";

const COPY: Record<
  NoOAuthClientsVariant,
  { title: string; body: string; showRegistryLink?: boolean; showSignOutHint?: boolean }
> = {
  platform_admin: {
    title: "Register an OAuth client first",
    body: "The application console is unavailable until at least one OAuth client exists. Connect an app to create an application and its client in one step.",
    showRegistryLink: true,
  },
  tenant_super_admin: {
    title: "OAuth client required",
    body: "No OAuth clients are registered yet. Sign in on the Platform operator tab, then use Connect an app to register the first client.",
    showSignOutHint: true,
  },
  tenant_operator: {
    title: "OAuth client required",
    body: "No OAuth clients are registered yet. Ask a platform administrator to register an OAuth client before you can use the application console.",
  },
};

export function NoOAuthClientsPanel({
  variant,
  className,
}: {
  variant: NoOAuthClientsVariant;
  className?: string;
}) {
  const copy = COPY[variant];
  // Connect an app can be turned off (Platform settings → Feature flags); then use the registry forms.
  const connectEnabled = useAdminContext().connectAppEnabled !== false;
  const onRegistry = usePathname() === "/applications";
  const body =
    variant === "platform_admin" && !connectEnabled
      ? "The application console is unavailable until at least one OAuth client exists. Register an application and its first OAuth client."
      : copy.body;

  return (
    <Card className={className ?? "p-6"}>
      <h2 className="text-base font-semibold text-ui">{copy.title}</h2>
      <p className="mt-2 text-sm text-muted">{body}</p>
      <div className="mt-4 flex flex-wrap gap-3 justify-center">
        {copy.showRegistryLink && (
          connectEnabled ? (
            <ButtonLink href="/connect" size="sm">
              Connect an app
            </ButtonLink>
          ) : onRegistry ? (
            <span className="text-sm text-muted">Use + Register application &amp; OAuth client above.</span>
          ) : (
            <ButtonLink href="/applications" size="sm">
              Go to OAuth clients
            </ButtonLink>
          )
        )}
        {copy.showSignOutHint && (
          <Link href="/login" className="text-sm text-brand hover:underline">
            Sign in as platform admin
          </Link>
        )}
      </div>
    </Card>
  );
}
