import { PageHeader } from "@/components/ui/PageHeader";
import { ConfigWorkspace } from "@/components/config/ConfigWorkspace";
import { listTenants } from "@/lib/api/tenants";
import { requirePlatformAccess } from "@/lib/platform-access";

export const dynamic = "force-dynamic";

export default async function ConfigPage() {
  await requirePlatformAccess();
  const tenants = await listTenants();
  return (
    <div className="w-full min-w-0">
      <PageHeader
        title="Configuration as code"
        description="Keep a tenant's setup in a file: export it, review changes in version control, and apply the same file to staging and production."
      />
      <ConfigWorkspace tenants={tenants} />
    </div>
  );
}
