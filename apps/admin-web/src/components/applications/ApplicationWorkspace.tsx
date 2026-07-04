"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { grantApplicationUser, revokeApplicationUser } from "@/lib/api/application-settings";
import { ApplicationSettingsEditor } from "@/components/applications/ApplicationSettingsEditor";
import { listUsers } from "@/lib/api/users";
import type { ApplicationProduct, Tenant, User } from "@/lib/types";

type Tab = "overview" | "users" | "settings";

export function ApplicationWorkspace({
  app,
  tenant,
  platformSettingsAccess = false,
}: {
  app: ApplicationProduct;
  tenant: Tenant | null;
  /** Platform operator (not tenant admin / act-as). */
  platformSettingsAccess?: boolean;
}) {
  const [tab, setTab] = useState<Tab>("overview");

  const tabs: { id: Tab; label: string }[] = [
    { id: "overview", label: "Overview" },
    { id: "users", label: "Users" },
    { id: "settings", label: "Settings" },
  ];

  return (
    <div>
      <div className="mb-6 flex flex-wrap gap-1 border-b border-ui">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t.id ? "border-ui-primary text-brand" : "border-transparent text-muted"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "overview" && (
        <OverviewPanel app={app} tenant={tenant} platformSettingsAccess={platformSettingsAccess} />
      )}
      {tab === "users" && (
        <UsersPanel
          applicationId={app.id}
          tenantId={app.tenantId}
          appName={app.name}
          tenantName={tenant?.name ?? app.tenantId}
        />
      )}
      {tab === "settings" && <ApplicationSettingsEditor appId={app.id} />}
    </div>
  );
}

function OverviewPanel({
  app,
  tenant,
  platformSettingsAccess,
}: {
  app: ApplicationProduct;
  tenant: Tenant | null;
  platformSettingsAccess: boolean;
}) {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <Card padded={false}>
        <CardHeader title="Application product" />
        <div className="space-y-4 p-5 text-sm">
          <Row label="Slug" value={<code className="font-mono">{app.slug}</code>} />
          <Row label="Schema" value={<code className="font-mono">{app.schemaName ?? "platform (legacy)"}</code>} />
          <Row label="Tenant" value={tenant?.name ?? app.tenantId} />
          <Row label="OAuth clients" value={String(app.oauthClientCount)} />
        </div>
      </Card>
      <Card padded={false}>
        <CardHeader title="Status" />
        <div className="space-y-3 p-5 text-sm">
          <Row label="Status" value={<Badge tone="indigo" className="uppercase">{app.status}</Badge>} />
          {app.description && <Row label="Description" value={app.description} />}
        </div>
      </Card>
      <p className="text-sm text-muted lg:col-span-2">
        {platformSettingsAccess ? (
          <>
            Platform-wide defaults live under{" "}
            <Link href="/settings" className="text-brand hover:underline">
              Platform settings
            </Link>
            .{" "}
          </>
        ) : null}
        Use the <strong>Users</strong> and <strong>Settings</strong> tabs here to manage this application.
      </p>
    </div>
  );
}

export function UsersPanel({
  applicationId,
  tenantId,
  appName,
  tenantName,
}: {
  applicationId: string;
  tenantId: string;
  appName: string;
  tenantName: string;
}) {
  const app = { id: applicationId, tenantId, name: appName } as ApplicationProduct;
  const tenant = { name: tenantName } as Tenant;
  const { toast } = useToast();
  const [members, setMembers] = useState<User[]>([]);
  const [tenantUsers, setTenantUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [m, all] = await Promise.all([
        listUsers(undefined, app.id),
        listUsers(app.tenantId),
      ]);
      setMembers(m);
      setTenantUsers(all.filter((u) => !m.some((x) => x.id === u.id)));
    } finally {
      setLoading(false);
    }
  }, [app.id, app.tenantId]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function addUser(userId: string) {
    await grantApplicationUser(app.id, userId);
    toast("User added to this application", "success");
    reload();
  }

  async function removeUser(userId: string) {
    await revokeApplicationUser(app.id, userId);
    toast("User removed from this application", "success");
    reload();
  }

  return (
    <div className="space-y-6">
      <Card padded={false}>
        <CardHeader
          title="Application members"
          description={`Users who can sign in to ${app.name} (${tenant?.name ?? "tenant"})`}
        />
        {loading ? (
          <p className="p-5 text-sm text-muted">Loading…</p>
        ) : members.length === 0 ? (
          <p className="p-5 text-sm text-muted">No users assigned yet. Add users from your tenant below.</p>
        ) : (
          <ul className="divide-y divide-ui">
            {members.map((u) => (
              <li key={u.id} className="flex items-center justify-between px-5 py-3">
                <div>
                  <Link href={`/users/${u.id}`} className="text-sm font-medium text-brand hover:underline">
                    {u.firstName} {u.lastName}
                  </Link>
                  <p className="text-xs text-muted">{u.email}</p>
                </div>
                <Button variant="ghost" size="sm" onClick={() => removeUser(u.id)}>
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card padded={false}>
        <CardHeader title="Add from tenant" description="Grant access without affecting other applications" />
        {tenantUsers.length === 0 ? (
          <p className="p-5 text-sm text-muted">All tenant users are already members.</p>
        ) : (
          <ul className="divide-y divide-ui">
            {tenantUsers.map((u) => (
              <li key={u.id} className="flex items-center justify-between px-5 py-3">
                <span className="text-sm">{u.email}</span>
                <Button variant="secondary" size="sm" onClick={() => addUser(u.id)}>
                  Add
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <div className="mt-1">{value}</div>
    </div>
  );
}
