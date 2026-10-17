import { PageHeader } from "@/components/ui/PageHeader";
import { ButtonLink } from "@/components/ui/Button";
import { StatCard } from "@/components/ui/StatCard";
import { Tooltip } from "@/components/ui/Tooltip";
import { ApplicationsWithoutClients } from "@/components/applications/ApplicationsWithoutClients";
import { ClientRegistry } from "@/components/applications/ClientRegistry";
import { RegisterApplicationWithClientFormModal } from "@/components/forms/RegisterApplicationWithClientFormModal";
import { OAuthClientFormModal } from "@/components/forms/OAuthClientFormModal";
import { listApplications } from "@/lib/api/applications";
import { listOAuthClients } from "@/lib/api/oauth-clients";
import { listTenants } from "@/lib/api/tenants";
import { requirePlatformAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

export default async function ApplicationsPage() {
  const ctx = await requirePlatformAccess();
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
            {ctx.connectAppEnabled !== false && (
              <Tooltip
                align="end"
                content="Recommended. Pick the kind of app (web, single-page, mobile, backend, TV or command line) and its framework. SecureOne creates the application and client with secure settings, gives you ready-to-use code and a test sign-in."
              >
                <ButtonLink href="/connect">Connect an app</ButtonLink>
              </Tooltip>
            )}
            <Tooltip
              align="end"
              content="A new application (your product: its users, roles and permissions) together with its first OAuth client, in one form. You choose every OAuth setting yourself: grant types, scopes, PKCE, redirect URLs."
            >
              <RegisterApplicationWithClientFormModal tenants={tenants} triggerVariant="secondary" />
            </Tooltip>
            <Tooltip
              align="end"
              content="Another OAuth client for an application that already exists, for example a mobile app next to the web app. Both share the application's users and roles."
            >
              <OAuthClientFormModal
                applications={applications}
                tenants={tenants}
                triggerLabel="+ Add OAuth client"
                triggerVariant="secondary"
              />
            </Tooltip>
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
      <ApplicationsWithoutClients applications={applications} tenants={tenants} />
    </div>
  );
}
