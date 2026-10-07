import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { PageHeader } from "@/components/ui/PageHeader";
import {
  UsersWorkspace,
  UsersWorkspaceHeaderActions,
  UsersWorkspaceProvider,
} from "@/components/users/UsersWorkspace";
import { resolveApplicationMeta } from "@/lib/api/app-workspace";
import { fetchUserDirectorySettings } from "@/lib/api/user-directory";
import { listUsers } from "@/lib/api/users";
import { listRoles } from "@/lib/api/roles";
import { listVisibleTenants } from "@/lib/api/operator-scope";
import { ApiError } from "@/lib/api/http";

export const dynamic = "force-dynamic";

export default async function AppUsersPage({
  params,
}: {
  params: Promise<{ applicationId: string }>;
}) {
  const { applicationId } = await params;
  const app = await resolveApplicationMeta(applicationId);
  if (!app) notFound();

  const [usersResult, roles, tenants, directory] = await Promise.all([
    listUsers(app.tenantId, applicationId).then(
      (rows) => ({ rows, error: null as string | null }),
      (e) => ({
        rows: [] as Awaited<ReturnType<typeof listUsers>>,
        error: e instanceof Error ? e.message : "Could not load users.",
      }),
    ),
    // Operators without role:read still get the user list, just without role names.
    listRoles({ applicationId }).catch((e) => {
      if (e instanceof ApiError && e.status === 403) return [];
      throw e;
    }),
    listVisibleTenants([app]),
    fetchUserDirectorySettings(applicationId).catch(() => null),
  ]);
  const users = usersResult.rows;
  const tenant = tenants.find((t) => t.id === app.tenantId);

  return (
    <UsersWorkspaceProvider>
      <div className="flex w-full min-w-0 flex-1 flex-col">
        <PageHeader
          breadcrumb={
            <Link href={`/app/${applicationId}/users`} className="hover:underline">
              Users
            </Link>
          }
          title="User management"
          description={`${app.name} · browse members, then open a user to manage account settings`}
          actions={
            tenant ? (
              <UsersWorkspaceHeaderActions
                tenants={[tenant]}
                roles={roles}
                tenantId={app.tenantId}
                applicationId={applicationId}
                directory={directory}
              />
            ) : undefined
          }
        />
        {usersResult.error && (
          <div className="mx-4 mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200">
            {usersResult.error} Restart auth-server if you recently updated the API.
          </div>
        )}
        <Suspense fallback={<div className="p-6 text-sm text-muted">Loading users…</div>}>
          <UsersWorkspace
            users={users}
            roles={roles}
            tenants={tenant ? [tenant] : tenants}
            tenantId={app.tenantId}
            applicationId={applicationId}
            appName={app.name}
            tenantName={app.tenantName}
          />
        </Suspense>
      </div>
    </UsersWorkspaceProvider>
  );
}
