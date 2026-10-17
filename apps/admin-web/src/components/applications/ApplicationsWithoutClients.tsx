import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { OAuthClientFormModal } from "@/components/forms/OAuthClientFormModal";
import { applicationProductDeleteAction } from "@/lib/actions";
import { statusTone } from "@/lib/status";
import type { ApplicationProduct, Tenant } from "@/lib/types";

/**
 * Applications with no OAuth client. The client table cannot show them (it lists clients), so without
 * this they are invisible: deleting an application's last client keeps the application and its data.
 */
export function ApplicationsWithoutClients({
  applications,
  tenants,
}: {
  applications: ApplicationProduct[];
  tenants: Tenant[];
}) {
  const orphans = applications.filter((a) => a.oauthClientCount === 0);
  if (orphans.length === 0) return null;
  const tenantName = new Map(tenants.map((t) => [t.id, t.name]));

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Applications without an OAuth client"
        description="Nothing can sign in to these yet. Add a client to use them, or delete the ones you no longer need."
      />
      <ul className="divide-y divide-[var(--ui-border)]">
        {orphans.map((app) => (
          <li key={app.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
            <div className="min-w-0">
              <p className="flex items-center gap-2 text-sm font-medium text-ui">
                {app.name}
                <Badge tone={statusTone(app.status)} className="capitalize">
                  {app.status}
                </Badge>
              </p>
              <p className="text-xs text-muted">
                <span className="font-mono">{app.slug}</span> · {tenantName.get(app.tenantId) ?? app.tenantId}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <OAuthClientFormModal
                applications={applications}
                tenants={tenants}
                defaultApplicationId={app.id}
                triggerLabel="Add OAuth client"
                triggerVariant="secondary"
                triggerSize="sm"
              />
              <ConfirmDialog
                action={applicationProductDeleteAction}
                id={app.id}
                triggerLabel="Delete application"
                triggerVariant="danger"
                triggerSize="sm"
                title={`Delete ${app.name}?`}
                message="Permanently deletes the application with its roles, permissions, groups, settings, sign-in providers and webhooks, and its database schema. People keep their tenant accounts but lose access to this application. This cannot be undone."
                confirmLabel="Delete application"
                confirmText={app.slug}
              />
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
