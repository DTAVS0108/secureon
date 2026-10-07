import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/PageHeader";
import {
  GroupsWorkspace,
  GroupsWorkspaceHeaderActions,
} from "@/components/groups/GroupsWorkspace";
import { resolveApplicationMeta } from "@/lib/api/app-workspace";
import { listGroups, probeGroupsApi } from "@/lib/api/groups";
import { listRoles } from "@/lib/api/roles";
import { listUsers } from "@/lib/api/users";
import { getVisibleApplication } from "@/lib/api/operator-scope";

export const dynamic = "force-dynamic";

export default async function AppGroupsPage({
  params,
}: {
  params: Promise<{ applicationId: string }>;
}) {
  const { applicationId } = await params;
  const appMeta = await resolveApplicationMeta(applicationId);
  if (!appMeta) notFound();

  let groups: Awaited<ReturnType<typeof listGroups>> = [];
  let roles: Awaited<ReturnType<typeof listRoles>> = [];
  let users: Awaited<ReturnType<typeof listUsers>> = [];
  let apiAvailable = false;

  try {
    apiAvailable = await probeGroupsApi(applicationId);
    if (apiAvailable) {
      [groups, roles, users] = await Promise.all([
        listGroups(applicationId),
        listRoles({ applicationId }),
        listUsers(undefined, applicationId),
      ]);
    } else {
      [roles, users] = await Promise.all([
        listRoles({ applicationId }),
        listUsers(undefined, applicationId),
      ]);
    }
  } catch {
    groups = [];
    roles = [];
    users = [];
  }

  const app = await getVisibleApplication(applicationId, appMeta);

  return (
    <div className="w-full min-w-0">
      <PageHeader
        breadcrumb={<Link href={`/app/${applicationId}/groups`} className="hover:underline">Groups</Link>}
        title="Group management"
        description={`${appMeta.name} · assign multiple roles to a group, then add members`}
        actions={
          app && apiAvailable ? (
            <GroupsWorkspaceHeaderActions
              roles={roles}
              users={users}
              tenantId={app.tenantId}
              applicationId={applicationId}
            />
          ) : undefined
        }
      />
      {app ? (
        <GroupsWorkspace
          groups={groups}
          roles={roles}
          users={users}
          tenantId={app.tenantId}
          applicationId={applicationId}
          appName={appMeta.name}
          apiAvailable={apiAvailable}
        />
      ) : (
        <p className="text-sm text-muted">Unable to load application context.</p>
      )}
    </div>
  );
}
