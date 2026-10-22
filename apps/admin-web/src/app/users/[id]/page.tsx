import { UserFormModal } from "@/components/forms/UserFormModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { PageHeader } from "@/components/ui/PageHeader";
import { UserSecurityTab } from "@/components/users/UserSecurityTab";
import { UserTwoStepPanel } from "@/components/users/UserTwoStepPanel";
import {
  userDeleteAction,
  userSetStatusAction,
} from "@/lib/actions";
import { listRoles } from "@/lib/api/roles";
import { listLoginEvents } from "@/lib/api/sessions";
import { listVisibleTenants } from "@/lib/api/operator-scope";
import { ApiError } from "@/lib/api/http";
import { getUser } from "@/lib/api/users";
import { initials } from "@/lib/format";
import { statusTone } from "@/lib/status";
import Link from "next/link";
import { notFound } from "next/navigation";
import { LocalTime } from "@/components/ui/LocalTime";

export default async function UserDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const user = await getUser(id);
  if (!user) notFound();

  const [logins, tenants, roles] = await Promise.all([
    // Platform-wide login history is platform-admin only; tenant operators see it per application.
    listLoginEvents({ userId: user.id }).catch((e) => {
      if (e instanceof ApiError && e.status === 403) return [];
      throw e;
    }),
    listVisibleTenants(),
    listRoles({ tenantId: user.tenantId }),
  ]);
  const fullName = `${user.firstName} ${user.lastName}`;
  const roleById = new Map(roles.map((r) => [r.id, r]));
  const tenantLabel = tenants.find((t) => t.id === user.tenantId)?.name ?? user.tenantId;

  return (
    <div className="">
      <PageHeader
        breadcrumb={<Link href="/users" className="hover:underline">Users</Link>}
        title={fullName}
        description={user.email}
        actions={
          <>
            <Badge tone={statusTone(user.status)} dot className="capitalize">{user.status}</Badge>
            <UserFormModal user={user} tenants={tenants} roles={roles} triggerLabel="Edit" triggerVariant="secondary" />
            {user.status === "active" ? (
              <form action={userSetStatusAction}>
                <input type="hidden" name="id" value={user.id} />
                <input type="hidden" name="status" value="suspended" />
                <Button type="submit" variant="danger">Suspend</Button>
              </form>
            ) : (
              <form action={userSetStatusAction}>
                <input type="hidden" name="id" value={user.id} />
                <input type="hidden" name="status" value="active" />
                <Button type="submit">Reactivate</Button>
              </form>
            )}
            <ConfirmDialog
              action={userDeleteAction}
              id={user.id}
              triggerLabel="Delete"
              triggerVariant="ghost"
              title={`Delete ${fullName}?`}
              message="This permanently deletes the user account and revokes their sessions."
            />
          </>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <div className="flex flex-col items-center text-center">
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-brand-muted text-2xl font-semibold text-brand">
              {initials(fullName)}
            </div>
            <p className="mt-3 text-lg font-semibold">{fullName}</p>
            <p className="text-sm text-black/50 dark:text-white/50">@{user.username}</p>
            <div className="mt-3 flex gap-2">
              {user.emailVerified ? <Badge tone="success" dot>Verified</Badge> : <Badge tone="warning">Unverified</Badge>}
            </div>
          </div>
          <dl className="mt-5 space-y-3 border-t border-black/5 pt-5 text-sm dark:border-white/5">
            <Row label="Tenant" value={tenantLabel} />
            <Row label="User ID" value={<code className="font-mono text-xs">{user.id}</code>} />
            <Row label="Created" value={<LocalTime value={user.createdAt} />} />
            <Row label="Last login" value={user.lastLoginAt ? <LocalTime value={user.lastLoginAt} format="datetime" /> : "Never"} />
          </dl>
        </Card>

        <div className="space-y-6 lg:col-span-2">
          <Card padded={false}>
            <CardHeader
              title="Security"
              description="Account access, email verification, password reset/set-password emails, and MFA overview."
            />
            <div className="border-t border-ui px-5 py-4">
              <UserSecurityTab
                user={user}
                mfaFactorCount={user.mfaFactors.length}
              />
            </div>
          </Card>

          <Card padded={false}>
            <CardHeader title="Roles" description="Assigned access via RBAC" action={<UserFormModal user={user} tenants={tenants} roles={roles} triggerLabel="Manage" triggerVariant="ghost" triggerSize="sm" />} />
            <ul className="divide-y divide-black/5 p-2 dark:divide-white/5">
              {user.roleIds.map((rid) => {
                const role = roleById.get(rid);
                return (
                  <li key={rid} className="flex items-center justify-between px-3 py-2.5">
                    <div>
                      <p className="text-sm font-medium">{role?.name ?? rid}</p>
                      <p className="text-xs text-black/45 dark:text-white/45">{role?.description}</p>
                    </div>
                    {role?.isComposite && <Badge tone="indigo">composite</Badge>}
                  </li>
                );
              })}
            </ul>
          </Card>

          <Card>
            <CardHeader title="Two-step verification" />
            <UserTwoStepPanel userId={user.id} />
          </Card>

          <Card padded={false}>
            <CardHeader title="Recent sign-ins" />
            {logins.length > 0 ? (
              <ul className="divide-y divide-black/5 p-2 dark:divide-white/5">
                {logins.map((l) => (
                  <li key={l.id} className="flex items-center justify-between px-3 py-2.5 text-sm">
                    <div>
                      <p className="font-medium">{l.location}</p>
                      <p className="text-xs text-black/45 dark:text-white/45">{l.device} · {l.ip}</p>
                    </div>
                    <div className="text-right">
                      <Badge tone={l.result === "success" ? "success" : l.result === "failure" ? "danger" : "warning"}>{l.method}</Badge>
                      <p className="mt-1 text-xs text-black/40 dark:text-white/40"><LocalTime value={l.timestamp} format="datetime" /></p>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="p-5 text-sm text-black/45 dark:text-white/45">No sign-in history.</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-black/50 dark:text-white/50">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  );
}
