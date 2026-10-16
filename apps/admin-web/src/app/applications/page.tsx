import { PageHeader } from "@/components/ui/PageHeader";
import { ButtonLink } from "@/components/ui/Button";
import { StatCard } from "@/components/ui/StatCard";
import { ClientRegistry } from "@/components/applications/ClientRegistry";
import { RegisterApplicationWithClientFormModal } from "@/components/forms/RegisterApplicationWithClientFormModal";
import { OAuthClientFormModal } from "@/components/forms/OAuthClientFormModal";
import { listApplications } from "@/lib/api/applications";
import { listOAuthClients } from "@/lib/api/oauth-clients";
import { listTenants } from "@/lib/api/tenants";
import { requirePlatformAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

export default async function ApplicationsPage() {
  await requirePlatformAccess();
  const [clients, applications, tenants] = await Promise.all([
    listOAuthClients(),
    listApplications(),
    listTenants(),
  ]);

  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="OAuth client registry"
        description="Register application products with OAuth credentials. Use one form for a new product + first client, or add extra OAuth clients to an existing application."
        actions={
          <div className="flex flex-wrap gap-2">
            <ButtonLink href="/connect">Connect an app</ButtonLink>
            <RegisterApplicationWithClientFormModal tenants={tenants} triggerVariant="secondary" />
            <OAuthClientFormModal
              applications={applications}
              tenants={tenants}
              triggerLabel="+ Add OAuth client"
              triggerVariant="secondary"
            />
          </div>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="OAuth clients" value={clients.length} />
        <StatCard label="Applications" value={applications.length} />
        <StatCard
          label="Confidential"
          value={clients.filter((c) => c.confidential).length}
        />
        <StatCard label="Active clients" value={clients.filter((c) => c.status === "active").length} />
      </div>

      <ClientRegistry clients={clients} tenants={tenants} applications={applications} />
    </div>
  );
}
