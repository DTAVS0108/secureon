"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { UserFormModal } from "@/components/forms/UserFormModal";
import { UserSecurityTab } from "@/components/users/UserSecurityTab";
import { Badge, ComingSoonBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import {
  userDeleteAction,
  userDeleteMfaFactorAction,
  userResetMfaAction,
  userResetMfaMethodAction,
} from "@/lib/actions";
import { fetchApplicationAuthMethods } from "@/lib/api/application-settings";
import {
  getApplicationUser,
  setUserStatusApi,
  unlockUserApi,
  updateUserAuthMethodsApi,
} from "@/lib/api/users";
import { impersonateUser } from "@/lib/api/service-accounts";
import { publicAuthServerUrl } from "@/lib/config";
import { listLoginEvents } from "@/lib/api/sessions";
import { initials } from "@/lib/format";
import { statusTone } from "@/lib/status";
import { isAuthMethodComingSoon } from "@/lib/coming-soon";
import type { AuthMethod, LoginEvent, MfaFactorType, Role, Tenant, User } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

const MFA_LABEL: Record<string, string> = {
  passkey: "Passkey",
  totp: "Authenticator (TOTP)",
  sms: "SMS",
  email: "Email OTP",
  push: "Push",
};

const MFA_METHOD_TO_FACTOR: Record<string, MfaFactorType> = {
  m_totp: "totp",
  m_passkey: "passkey",
  m_sms: "sms",
  m_email_otp: "email",
  m_push: "push",
};

function factorTypeForMethodId(methodId: string): MfaFactorType | undefined {
  return MFA_METHOD_TO_FACTOR[methodId];
}

type Tab = "overview" | "security" | "mfa" | "roles" | "auth" | "signins";

export function UserDetailPanel({
  userSummary,
  applicationId,
  tenantId,
  tenantName,
  appName,
  roles,
  tenants,
  onBack,
  onUserChanged,
  onDeleted,
}: {
  userSummary: User;
  applicationId: string;
  tenantId: string;
  tenantName: string;
  appName: string;
  roles: Role[];
  tenants: Tenant[];
  onBack: () => void;
  onUserChanged?: () => void;
  onDeleted: () => void;
}) {
  const [user, setUser] = useState<User>(userSummary);
  const [actionBusy, setActionBusy] = useState(false);
  const [tab, setTab] = useState<Tab>("overview");
  const [logins, setLogins] = useState<LoginEvent[]>([]);
  const [authMethods, setAuthMethods] = useState<AuthMethod[]>([]);
  const [loading, setLoading] = useState(true);
  const [authSaving, setAuthSaving] = useState<string | null>(null);

  const fullName = `${user.firstName} ${user.lastName}`.trim() || user.email;
  const roleById = new Map(roles.map((r) => [r.id, r]));

  const enabledAppMethods = authMethods.filter((m) => m.enabled);
  const enabledMfaMethods = enabledAppMethods.filter((m) => m.category === "mfa");
  const enabledSignInMethods = enabledAppMethods.filter(
    (m) => m.category === "primary" || m.category === "federation",
  );
  const showMfaTab = enabledMfaMethods.length > 0;
  const showAuthTab = enabledSignInMethods.length > 0;
  const enabledMfaFactorTypes = new Set(
    enabledMfaMethods.map((m) => factorTypeForMethodId(m.id)).filter(Boolean) as MfaFactorType[],
  );
  const visibleMfaFactors = user.mfaFactors.filter(
    (f) => enabledMfaFactorTypes.size === 0 || enabledMfaFactorTypes.has(f.type),
  );
  const allowedAuth = user.allowedAuthMethods ?? {};

  useDeferredEffect(() => {
    setUser((prev) => {
      if (prev.id !== userSummary.id) return userSummary;
      return {
        ...prev,
        firstName: userSummary.firstName,
        lastName: userSummary.lastName,
        email: userSummary.email,
        username: userSummary.username,
        status: userSummary.status,
        emailVerified: userSummary.emailVerified,
        hasPassword: userSummary.hasPassword,
        roleIds: userSummary.roleIds,
      };
    });
  }, [userSummary]);

  useDeferredEffect(() => {
    setLoading(true);
    Promise.all([
      getApplicationUser(applicationId, userSummary.id).then((u) => u && setUser(u)),
      listLoginEvents({ userId: userSummary.id, applicationId }).then(setLogins).catch(() => setLogins([])),
      fetchApplicationAuthMethods(applicationId).then(setAuthMethods).catch(() => setAuthMethods([])),
    ]).finally(() => setLoading(false));
  }, [userSummary.id, applicationId]);

  async function applyUserChange(action: () => Promise<void>) {
    setActionBusy(true);
    try {
      await action();
      await reloadUser();
      onUserChanged?.();
    } finally {
      setActionBusy(false);
    }
  }

  useDeferredEffect(() => {
    if (tab === "mfa" && !showMfaTab) setTab("overview");
    if (tab === "auth" && !showAuthTab) setTab("overview");
  }, [tab, showMfaTab, showAuthTab]);

  async function reloadUser() {
    const u = await getApplicationUser(applicationId, user.id);
    if (u) setUser(u);
  }

  async function toggleUserAuthMethod(methodId: string, enabled: boolean) {
    setAuthSaving(methodId);
    try {
      const updated = await updateUserAuthMethodsApi(applicationId, user.id, {
        [methodId]: enabled,
      });
      setUser(updated);
      onUserChanged?.();
    } finally {
      setAuthSaving(null);
    }
  }

  function afterMfaFormSubmit() {
    window.setTimeout(() => {
      void reloadUser().then(() => onUserChanged?.());
    }, 400);
  }

  async function handleUserUpdated() {
    await reloadUser();
    onUserChanged?.();
  }

  const hiddenApp = <input type="hidden" name="applicationId" value={applicationId} />;

  const tabs: { id: Tab; label: string; count?: number }[] = [
    { id: "overview", label: "Overview" },
    { id: "security", label: "Security" },
    ...(showMfaTab
      ? [{ id: "mfa" as const, label: "MFA", count: visibleMfaFactors.length }]
      : []),
    { id: "roles", label: "Roles", count: user.roleIds.length },
    ...(showAuthTab ? [{ id: "auth" as const, label: "Sign-in methods" }] : []),
    { id: "signins", label: "Sign-ins", count: logins.length },
  ];

  return (
    <Card padded={false} className="flex w-full min-w-0 min-h-0 flex-1 flex-col overflow-hidden">
      <div className="border-b border-ui bg-ui-surface/80 px-5 py-4">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="-ml-2 mb-3 text-muted"
          onClick={onBack}
        >
          ← Back to user list
        </Button>
        <div className="flex flex-wrap items-start gap-4">
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-muted text-lg font-semibold text-brand">
            {initials(fullName)}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-lg font-semibold text-ui">{fullName}</h2>
              <Badge tone={statusTone(user.status)} dot className="capitalize">
                {user.status}
              </Badge>
              {user.locked && <Badge tone="danger">Locked</Badge>}
              {user.emailVerified ? (
                <Badge tone="success">Email verified</Badge>
              ) : (
                <Badge tone="warning">Unverified</Badge>
              )}
            </div>
            <p className="text-sm text-muted">{user.email}</p>
            <p className="text-xs text-faint">
              @{user.username} · {tenantName} · {appName}
            </p>
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            <UserFormModal
              key={`${user.id}-${user.emailVerified}`}
              user={user}
              tenants={tenants}
              roles={roles}
              applicationId={applicationId}
              tenantId={tenantId}
              lockToApp
              onUserUpdated={handleUserUpdated}
              triggerLabel="Edit"
              triggerVariant="secondary"
              triggerSize="sm"
            />
            <ConfirmDialog
              action={async (fd) => {
                fd.set("applicationId", applicationId);
                await userDeleteAction(fd);
                onDeleted();
              }}
              id={user.id}
              triggerLabel="Delete"
              triggerVariant="danger"
              triggerSize="sm"
              title={`Delete ${fullName}?`}
              message="Permanently removes this account and all MFA factors."
              confirmLabel="Delete"
            />
          </div>
        </div>

        <nav className="-mb-px mt-4 flex flex-wrap gap-1 border-b border-ui" aria-label="User sections">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`relative px-3 py-2 text-sm font-medium transition-colors ${
                tab === t.id
                  ? "text-brand after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-brand"
                  : "text-muted hover:text-ui"
              }`}
            >
              {t.label}
              {t.count !== undefined && (
                <span className="ml-1 rounded-full bg-ui-elevated px-1.5 text-[10px] font-semibold text-faint">
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </nav>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto p-5">
        {loading && tab !== "overview" ? (
          <p className="text-sm text-muted">Loading…</p>
        ) : null}

        {tab === "overview" && (
          <div className="flex min-h-0 flex-1 flex-col gap-6">
            <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
              <InfoRow label="User ID" value={<code className="text-xs break-all">{user.id}</code>} />
              <InfoRow label="Tenant" value={tenantName} />
              <InfoRow label="Application" value={appName} />
              <InfoRow label="Created" value={<LocalTime value={user.createdAt} />} />
              <InfoRow
                label="Last login"
                value={user.lastLoginAt ? <LocalTime value={user.lastLoginAt} format="datetime" /> : "Never"}
              />
              <InfoRow
                label="Phone"
                value={user.phone ? `${user.phone}${user.phoneVerified ? " (verified)" : " (not verified)"}` : "None"}
              />
              <InfoRow label="Password set" value={user.hasPassword ? "Yes" : "No (invite pending)"} />
              <InfoRow label="Failed logins" value={String(user.failedLoginCount ?? 0)} />
              <InfoRow
                label="MFA factors"
                value={user.mfaFactors.length > 0 ? String(user.mfaFactors.length) : "None"}
              />
              <InfoRow
                label="Roles"
                value={
                  user.roleIds.length > 0
                    ? user.roleIds
                        .map((id) => roleById.get(id)?.name ?? id)
                        .join(", ")
                    : "None assigned"
                }
              />
            </dl>
            <div className="flex flex-wrap gap-2">
              {user.status === "active" ? (
                <Button
                  type="button"
                  variant="danger"
                  size="sm"
                  disabled={actionBusy}
                  onClick={() =>
                    applyUserChange(() => setUserStatusApi(user.id, "suspended").then(() => undefined))
                  }
                >
                  Suspend
                </Button>
              ) : (
                <Button
                  type="button"
                  size="sm"
                  disabled={actionBusy}
                  onClick={() =>
                    applyUserChange(() => setUserStatusApi(user.id, "active").then(() => undefined))
                  }
                >
                  Activate
                </Button>
              )}
              {user.locked && (
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  disabled={actionBusy}
                  onClick={() => applyUserChange(() => unlockUserApi(user.id).then(() => undefined))}
                >
                  Unlock account
                </Button>
              )}
              {user.status === "active" && (
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  disabled={actionBusy}
                  onClick={async () => {
                    const reason = window.prompt("Reason for impersonation (audited):");
                    if (!reason?.trim()) return;
                    setActionBusy(true);
                    try {
                      const res = await impersonateUser(user.id, {
                        reason: reason.trim(),
                        applicationId,
                      });
                      const url = res.consumeUrl.startsWith("http")
                        ? res.consumeUrl
                        : `${publicAuthServerUrl()}${res.consumeUrl}`;
                      window.open(url, "_blank", "noopener,noreferrer");
                    } catch (e) {
                      window.alert(e instanceof Error ? e.message : "Impersonation failed");
                    } finally {
                      setActionBusy(false);
                    }
                  }}
                >
                  Impersonate
                </Button>
              )}
            </div>

            <div className="flex min-h-0 flex-1 flex-col border-t border-ui pt-6">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">
                Account sections
              </p>
              <div className="mt-3 grid flex-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
                <SectionShortcut
                  title="Security"
                  description="Account access, email verification, password emails, and MFA shortcuts."
                  onClick={() => setTab("security")}
                />
                {showMfaTab && (
                  <SectionShortcut
                    title="MFA"
                    description={
                      visibleMfaFactors.length > 0
                        ? `${visibleMfaFactors.length} enrolled factor(s)`
                        : "No factors enrolled yet"
                    }
                    onClick={() => setTab("mfa")}
                  />
                )}
                <SectionShortcut
                  title="Roles"
                  description={
                    user.roleIds.length > 0
                      ? `${user.roleIds.length} role(s) assigned`
                      : "No roles assigned"
                  }
                  onClick={() => setTab("roles")}
                />
                {showAuthTab && (
                  <SectionShortcut
                    title="Sign-in methods"
                    description="Per-user allow list for app-enabled authentication."
                    onClick={() => setTab("auth")}
                  />
                )}
                <SectionShortcut
                  title="Sign-ins"
                  description={
                    logins.length > 0
                      ? `${logins.length} event(s) for this app`
                      : "No sign-in history yet"
                  }
                  onClick={() => setTab("signins")}
                />
              </div>

              {logins.length > 0 && (
                <div className="mt-6">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted">
                      Recent sign-ins
                    </p>
                    <button
                      type="button"
                      onClick={() => setTab("signins")}
                      className="text-xs font-medium text-brand hover:underline"
                    >
                      View all →
                    </button>
                  </div>
                  <ul className="divide-y divide-ui rounded-xl border border-ui">
                    {logins.slice(0, 3).map((l) => (
                      <SignInRow key={l.id} login={l} />
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        )}

        {tab === "security" && (
          <UserSecurityTab
            user={user}
            applicationId={applicationId}
            mfaFactorCount={visibleMfaFactors.length}
            showMfaLink={showMfaTab}
            onChanged={async () => {
              await reloadUser();
              onUserChanged?.();
            }}
            onOpenMfaTab={() => setTab("mfa")}
            onOpenSignInsTab={() => setTab("signins")}
          />
        )}

        {tab === "mfa" && showMfaTab && (
          <div className="flex min-h-0 flex-1 flex-col gap-6">
            <section>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-muted">
                  MFA types enabled for this application. Remove factors or reset by method.
                </p>
                {visibleMfaFactors.length > 0 && (
                  <form action={userResetMfaAction} onSubmit={afterMfaFormSubmit}>
                    <input type="hidden" name="id" value={user.id} />
                    {hiddenApp}
                    <Button type="submit" variant="danger" size="sm">
                      Reset all MFA
                    </Button>
                  </form>
                )}
              </div>
              {visibleMfaFactors.length > 0 ? (
                <ul className="divide-y divide-ui rounded-xl border border-ui">
                  {visibleMfaFactors.map((f) => (
                    <li key={f.id} className="flex items-center justify-between gap-3 px-4 py-3">
                      <div>
                        <p className="text-sm font-medium text-ui">
                          {MFA_LABEL[f.type] ?? f.type}
                        </p>
                        <p className="text-xs text-muted">
                          {f.label} · added <LocalTime value={f.addedAt} />
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge tone={f.verified ? "success" : "warning"}>
                          {f.verified ? "Verified" : "Pending"}
                        </Badge>
                        <form action={userDeleteMfaFactorAction} onSubmit={afterMfaFormSubmit}>
                          <input type="hidden" name="id" value={user.id} />
                          <input type="hidden" name="factorId" value={f.id} />
                          {hiddenApp}
                          <Button type="submit" variant="ghost" size="sm">
                            Remove
                          </Button>
                        </form>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="rounded-xl border border-dashed border-ui px-6 py-8 text-center text-sm text-muted">
                  No enrolled factors for enabled MFA methods.
                </p>
              )}
            </section>
            <section>
              <h3 className="text-sm font-semibold text-ui">By MFA method</h3>
              <ul className="mt-2 space-y-2">
                {enabledMfaMethods.map((m) => {
                  const factorType = factorTypeForMethodId(m.id);
                  const enrolled = factorType
                    ? user.mfaFactors.filter((f) => f.type === factorType)
                    : [];
                  return (
                    <li
                      key={m.id}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-ui px-3 py-2"
                    >
                      <div>
                        <p className="text-sm font-medium text-ui">{m.name}</p>
                        <p className="text-xs text-muted">
                          {enrolled.length > 0
                            ? `${enrolled.length} enrolled factor(s)`
                            : "Not enrolled"}
                        </p>
                      </div>
                      {enrolled.length > 0 && (
                        <form action={userResetMfaMethodAction} onSubmit={afterMfaFormSubmit}>
                          <input type="hidden" name="id" value={user.id} />
                          <input type="hidden" name="methodId" value={m.id} />
                          {hiddenApp}
                          <Button type="submit" variant="secondary" size="sm">
                            Reset {m.name}
                          </Button>
                        </form>
                      )}
                    </li>
                  );
                })}
              </ul>
            </section>
          </div>
        )}

        {tab === "roles" && (
          <div className="flex min-h-0 flex-1 flex-col">
            <div className="mb-3 flex justify-between">
              <p className="text-sm text-muted">RBAC roles assigned to this user.</p>
              <UserFormModal
                key={`${user.id}-${user.emailVerified}-roles`}
                user={user}
                tenants={tenants}
                roles={roles}
                applicationId={applicationId}
                tenantId={tenantId}
                lockToApp
                onUserUpdated={handleUserUpdated}
                triggerLabel="Manage roles"
                triggerVariant="ghost"
                triggerSize="sm"
              />
            </div>
            <ul className="divide-y divide-ui rounded-xl border border-ui">
              {user.roleIds.length === 0 ? (
                <li className="px-4 py-6 text-center text-sm text-muted">No roles assigned.</li>
              ) : (
                user.roleIds.map((rid) => {
                  const role = roleById.get(rid);
                  return (
                    <li key={rid} className="flex items-center justify-between px-4 py-3">
                      <div>
                        <p className="text-sm font-medium text-ui">{role?.name ?? rid}</p>
                        <p className="text-xs text-muted">{role?.description}</p>
                      </div>
                      {role?.isComposite && <Badge tone="indigo">Composite</Badge>}
                    </li>
                  );
                })
              )}
            </ul>
          </div>
        )}

        {tab === "auth" && showAuthTab && (
          <div className="flex min-h-0 flex-1 flex-col gap-4">
            <p className="text-sm text-muted">
              Allow or block sign-in methods for this user. Only methods enabled for{" "}
              <strong className="text-ui">{appName}</strong> appear here. Change app-wide options in{" "}
              <Link href={`/app/${applicationId}/settings`} className="text-brand hover:underline">
                Application settings
              </Link>
              .
            </p>
            <UserAuthMethodToggles
              title="Primary sign-in"
              methods={enabledSignInMethods.filter((m) => m.category === "primary")}
              allowed={allowedAuth}
              savingId={authSaving}
              onToggle={toggleUserAuthMethod}
            />
            <UserAuthMethodToggles
              title="Federation / SSO"
              methods={enabledSignInMethods.filter((m) => m.category === "federation")}
              allowed={allowedAuth}
              savingId={authSaving}
              onToggle={toggleUserAuthMethod}
            />
            {enabledMfaMethods.length > 0 && (
              <UserAuthMethodToggles
                title="Multi-factor (allow at sign-in)"
                methods={enabledMfaMethods}
                allowed={allowedAuth}
                savingId={authSaving}
                onToggle={toggleUserAuthMethod}
              />
            )}
            <p className="text-xs text-faint">
              Disabling a method blocks this user from using it; app settings must still have the method enabled.
            </p>
          </div>
        )}

        {tab === "signins" && (
          <div className="flex min-h-0 flex-1 flex-col">
            {logins.length > 0 ? (
              <ul className="divide-y divide-ui rounded-xl border border-ui">
                {logins.map((l) => (
                  <SignInRow key={l.id} login={l} />
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted">No sign-in history for this application.</p>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}

/** One sign-in attempt: outcome first (location is often unknown), then device, method and time. */
function SignInRow({
  login,
}: {
  login: { result: string; location?: string | null; device?: string | null; ip?: string | null; method: string; timestamp: string };
}) {
  const ok = login.result === "success";
  const location = login.location && login.location !== "—" ? login.location : null;
  return (
    <li className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
      <div>
        <p className="font-medium text-ui">
          {ok ? "Signed in" : "Failed sign-in"}
          {location ? ` · ${location}` : ""}
        </p>
        <p className="text-xs text-muted">{[login.device, login.ip].filter(Boolean).join(" · ")}</p>
      </div>
      <div className="text-right">
        <Badge tone={ok ? "success" : "danger"}>{login.method}</Badge>
        <p className="mt-1 text-xs text-faint"><LocalTime value={login.timestamp} format="datetime" /></p>
      </div>
    </li>
  );
}

function SectionShortcut({
  title,
  description,
  onClick,
}: {
  title: string;
  description: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-full min-h-[5.5rem] flex-col rounded-xl border border-ui bg-ui-elevated/40 px-4 py-3 text-left transition-colors hover:border-brand/40 hover:bg-brand-muted/20"
    >
      <span className="text-sm font-medium text-ui">{title}</span>
      <span className="mt-1 flex-1 text-xs text-muted">{description}</span>
      <span className="mt-2 text-xs font-medium text-brand">Open →</span>
    </button>
  );
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-lg border border-ui bg-ui-elevated/30 px-3 py-2">
      <dt className="text-[10px] font-semibold uppercase tracking-wide text-faint">{label}</dt>
      <dd className="mt-0.5 text-sm text-ui">{value}</dd>
    </div>
  );
}

function UserAuthMethodToggles({
  title,
  methods,
  allowed,
  savingId,
  onToggle,
}: {
  title: string;
  methods: AuthMethod[];
  allowed: Record<string, boolean>;
  savingId: string | null;
  onToggle: (methodId: string, enabled: boolean) => void;
}) {
  if (methods.length === 0) return null;
  return (
    <div>
      <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">{title}</h4>
      <ul className="mt-2 space-y-2">
        {methods.map((m) => {
          const allowedForUser = allowed[m.id] !== false;
          return (
            <li
              key={m.id}
              className="flex items-start justify-between gap-3 rounded-lg border border-ui px-3 py-2"
            >
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-ui">{m.name}</p>
                  {isAuthMethodComingSoon(m) && <ComingSoonBadge />}
                </div>
                <p className="text-xs text-muted">{m.description}</p>
              </div>
              <label className="flex shrink-0 cursor-pointer items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-ui text-brand"
                  checked={allowedForUser}
                  disabled={savingId === m.id}
                  onChange={(e) => onToggle(m.id, e.target.checked)}
                />
                <span className="text-muted">{allowedForUser ? "Allowed" : "Blocked"}</span>
              </label>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
