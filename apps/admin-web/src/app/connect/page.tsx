import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import { ConnectWizard } from "@/components/connect/ConnectWizard";
import { listApplications } from "@/lib/api/applications";
import { listTenants } from "@/lib/api/tenants";
import { getPlatformAccessContext } from "@/lib/platform-access";
import type { ApplicationProduct, Tenant } from "@/lib/types";

export const dynamic = "force-dynamic";

const DESCRIPTION = "Pick what you are building; SecureOne registers it with secure settings and gives you setup code for your framework.";

/**
 * Platform operators connect apps for any tenant and may create applications. The tenant super admin
 * (every application of the tenant) and tenant admins (the applications assigned to them) add clients
 * to applications they manage; the server checks the same rule on every call.
 */
export default async function ConnectPage({
  searchParams,
}: {
  searchParams: Promise<{ applicationId?: string }>;
}) {
  const ctx = await getPlatformAccessContext();
  // Platform settings → Feature flags → Connect an app.
  if (ctx.connectAppEnabled === false) notFound();
  const { applicationId } = await searchParams;

  if (ctx.platformSuperAdmin) {
    const [tenants, applications] = await Promise.all([listTenants(), listApplications()]);
    return (
      <div className="w-full min-w-0">
        <PageHeader
          breadcrumb={<Link href="/applications" className="hover:underline">OAuth clients</Link>}
          title="Connect an app"
          description={DESCRIPTION}
        />
        <ConnectWizard tenants={tenants} applications={applications} initialApplicationId={applicationId} />
      </div>
    );
  }

  const managed = ctx.applications.filter((a) => a.canManageClients);
  if (managed.length === 0 || !ctx.tenantId) {
    notFound();
  }
  const tenant: Tenant = {
    id: ctx.tenantId,
    name: ctx.tenantName ?? ctx.tenantSlug ?? "",
    slug: ctx.tenantSlug ?? "",
    status: "active",
    plan: "team",
    userCount: 0,
    appCount: managed.length,
    createdAt: "",
  };
  const applications: ApplicationProduct[] = managed.map((a) => ({
    id: a.id,
    tenantId: a.tenantId,
    name: a.name,
    slug: a.slug,
    status: a.status.toLowerCase() as ApplicationProduct["status"],
    oauthClientCount: 0,
    createdAt: "",
    updatedAt: "",
  }));
  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="Connect an app"
        description={`${DESCRIPTION} You can add clients to the applications you manage in ${tenant.name}.`}
      />
      <ConnectWizard
        tenants={[tenant]}
        applications={applications}
        initialApplicationId={applicationId}
        canCreateApplications={false}
      />
    </div>
  );
}
