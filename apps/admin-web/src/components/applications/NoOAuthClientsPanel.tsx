import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import type { NoOAuthClientsVariant } from "@/lib/oauth-client-registry";
import Link from "next/link";

const COPY: Record<
  NoOAuthClientsVariant,
  { title: string; body: string; showRegistryLink?: boolean; showSignOutHint?: boolean }
> = {
  platform_admin: {
    title: "Register an OAuth client first",
    body: "The application console is unavailable until at least one OAuth client exists. Create a tenant and application product if needed, then register an OAuth client for that application.",
    showRegistryLink: true,
  },
  tenant_super_admin: {
    title: "OAuth client required",
    body: "No OAuth clients are registered yet. Sign out, sign in with platform administrator credentials (leave the tenant slug empty), then open OAuth clients and register the first client.",
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

  return (
    <Card className={className ?? "p-6"}>
      <h2 className="text-base font-semibold text-ui">{copy.title}</h2>
      <p className="mt-2 text-sm text-muted">{copy.body}</p>
      <div className="mt-4 flex flex-wrap gap-3 justify-center">
        {copy.showRegistryLink && (
          <ButtonLink href="/applications" size="sm">
            Go to OAuth clients
          </ButtonLink>
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
