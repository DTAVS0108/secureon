import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { ClientDetailPanel } from "@/components/applications/ClientDetailPanel";
import { IsolateApplicationButton } from "@/components/applications/IsolateApplicationButton";
import { OAuthClientFormModal } from "@/components/forms/OAuthClientFormModal";
import { listApplications } from "@/lib/api/applications";
import { getOAuthClient } from "@/lib/api/oauth-clients";
import { getPlatformAccessContext } from "@/lib/platform-access";
import type { ApplicationProduct } from "@/lib/types";
import { statusTone } from "@/lib/status";

export default async function ApplicationClientPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const ctx = await getPlatformAccessContext();
  const { id } = await params;
  const platform = ctx.platformSuperAdmin;

  // Tenant super admins and tenant admins open the clients of applications they manage (docs/25).
  const client = await getOAuthClient(id).catch(() => null);
  if (!client) notFound();
  const managed = ctx.applications.find((a) => a.id === client.applicationId && a.canManageClients);
  if (!platform && !managed) notFound();
  const applications: ApplicationProduct[] = platform
    ? await listApplications()
    : [{
        id: managed!.id,
        tenantId: managed!.tenantId,
        name: managed!.name,
        slug: managed!.slug,
        status: managed!.status.toLowerCase() as ApplicationProduct["status"],
        oauthClientCount: 0,
        createdAt: "",
        updatedAt: "",
      }];
  const application = applications.find((a) => a.id === client.applicationId);

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={
          platform
            ? <Link href="/applications" className="hover:underline">OAuth clients</Link>
            : ctx.connectAppEnabled !== false
              ? <Link href="/connect" className="hover:underline">Connect an app</Link>
              : <Link href={`/app/${client.applicationId}/users`} className="hover:underline">{client.applicationName}</Link>
        }
        title={client.clientId}
        description={`OAuth client for ${client.applicationName}`}
        actions={
          <>
            {platform && application && <IsolateApplicationButton application={application} />}
            <Link href={`/app/${client.applicationId}/users`}>
              <Button variant="secondary">Application console →</Button>
            </Link>
            <Badge tone={statusTone(client.status)} dot className="capitalize">
              {client.status}
            </Badge>
            <OAuthClientFormModal
              client={client}
              applications={application ? [application] : applications}
              triggerLabel="Edit client"
              triggerVariant="secondary"
            />
          </>
        }
      />

      <ClientDetailPanel client={client} applicationName={application?.name ?? client.applicationName} />
    </div>
  );
}
