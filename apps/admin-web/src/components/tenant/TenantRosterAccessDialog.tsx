"use client";

import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  CONSOLE_FEATURE_LABELS,
  computeEffectiveFeatures,
  fetchConsoleCapabilityCatalog,
  fetchUserConsoleCapabilities,
  updateUserConsoleCapabilities,
  type ConsoleFeatureOverride,
} from "@/lib/api/admin-console-capabilities";
import {
  CONSOLE_ROLE_LABELS,
  grantConsoleAccess,
  revokeConsoleAccess,
  type AdminConsoleRoleType,
  type ConsoleAccessAssignment,
} from "@/lib/api/admin-console-access";
import { loadTenantConsoleRolesCatalog } from "@/lib/api/tenant-console-roles";
import {
  fetchUserTenantRoleAssignments,
  listTenantRoles,
  replaceUserTenantRoleAssignments,
  type TenantRole,
} from "@/lib/api/tenant-rbac";
import { getRole, listRoles } from "@/lib/api/roles";
import {
  updateUserApplicationRoles,
  userApplicationAccess,
  type TenantWorkspaceUser,
  type TenantWorkspaceUserPatch,
} from "@/lib/api/tenant-workspace";
import type { Role, RoleDetail } from "@/lib/types";

type Tab = "console" | "features" | "roles" | "tenantRoles";

export function TenantRosterAccessDialog({
  open,
  user,
  applications,
  tenantId,
  consoleAssignments,
  initialTab = "console",
  onClose,
  onSaved,
}: {
  open: boolean;
  user: TenantWorkspaceUser | null;
  applications: { id: string; name: string }[];
  tenantId: string;
  consoleAssignments: ConsoleAccessAssignment[];
  initialTab?: Tab;
  onClose: () => void;
  onSaved: (patch?: TenantWorkspaceUserPatch) => Promise<void>;
}) {
  const { toast } = useToast();
  const [tab, setTab] = useState<Tab>("console");
  const [appId, setAppId] = useState(applications[0]?.id ?? "");
  const [roles, setRoles] = useState<Role[]>([]);
  const [roleDetails, setRoleDetails] = useState<Map<string, RoleDetail>>(new Map());
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [savedRoleIds, setSavedRoleIds] = useState<string[]>([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [savingRoles, setSavingRoles] = useState(false);
  const [catalogFeatures, setCatalogFeatures] = useState<string[]>([]);
  const [roleDefaults, setRoleDefaults] = useState<string[]>([]);
  const [savedOverrides, setSavedOverrides] = useState<ConsoleFeatureOverride[]>([]);
  const [overrides, setOverrides] = useState<ConsoleFeatureOverride[]>([]);
  const [loadingCaps, setLoadingCaps] = useState(false);
  const [savingCaps, setSavingCaps] = useState(false);
  const [revokeBusy, setRevokeBusy] = useState<string | null>(null);
  const [grantRoleType, setGrantRoleType] = useState<AdminConsoleRoleType>("APPLICATION_ADMIN");
  const [grantAppId, setGrantAppId] = useState(applications[0]?.id ?? "");
  const [granting, setGranting] = useState(false);
  const [consoleRoleCatalog, setConsoleRoleCatalog] = useState<
    Record<string, string[]>
  >({});
  const [tenantGovernanceRoles, setTenantGovernanceRoles] = useState<TenantRole[]>([]);
  const [tenantRoleIds, setTenantRoleIds] = useState<string[]>([]);
  const [savedTenantRoleIds, setSavedTenantRoleIds] = useState<string[]>([]);
  const [loadingTenantRoles, setLoadingTenantRoles] = useState(false);
  const [savingTenantRoles, setSavingTenantRoles] = useState(false);

  const memberApps = useMemo(
    () => applications.filter((a) => user?.applicationIds.includes(a.id)),
    [applications, user?.applicationIds],
  );

  const needsGrantApp = grantRoleType !== "TENANT_SUPER_ADMIN";

  useDeferredEffect(() => {
    if (!open || !user) return;
    setTab(initialTab);
    const first = memberApps[0]?.id ?? applications[0]?.id ?? "";
    setAppId(first);
    setGrantAppId(first);
    setGrantRoleType("APPLICATION_ADMIN");
  }, [open, user?.id, memberApps, applications, initialTab]);

  useDeferredEffect(() => {
    if (!open || !user) return;
    let cancelled = false;
    void loadTenantConsoleRolesCatalog(tenantId)
      .then((catalog) => {
        if (cancelled) return;
        const defaults: Record<string, string[]> = {};
        for (const role of catalog.roles) {
          defaults[role.roleType] = role.defaultFeatures;
        }
        setConsoleRoleCatalog(defaults);
      })
      .catch(() => {
        if (!cancelled) setConsoleRoleCatalog({});
      });
    return () => {
      cancelled = true;
    };
  }, [open, tenantId, user?.id]);

  useDeferredEffect(() => {
    if (!open || !user) return;
    let cancelled = false;
    setLoadingTenantRoles(true);
    void Promise.all([
      listTenantRoles(tenantId).then((rows) => rows.filter((r) => !r.systemRole)),
      fetchUserTenantRoleAssignments(tenantId, user.id),
    ])
      .then(([roles, assignments]) => {
        if (cancelled) return;
        setTenantGovernanceRoles(roles);
        setTenantRoleIds(assignments.roleIds);
        setSavedTenantRoleIds(assignments.roleIds);
      })
      .catch(() => {
        if (!cancelled) {
          setTenantGovernanceRoles([]);
          setTenantRoleIds([]);
          setSavedTenantRoleIds([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingTenantRoles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, user?.id, tenantId]);

  useDeferredEffect(() => {
    if (!open || !user) return;
    let cancelled = false;
    setLoadingCaps(true);
    void Promise.all([
      fetchConsoleCapabilityCatalog(tenantId, user.id),
      fetchUserConsoleCapabilities(tenantId, user.id),
    ])
      .then(([catalog, caps]) => {
        if (cancelled) return;
        setCatalogFeatures(catalog.features);
        setRoleDefaults(caps.roleDefaults);
        setOverrides(caps.overrides);
        setSavedOverrides(caps.overrides);
      })
      .catch(() => {
        if (!cancelled) {
          setCatalogFeatures([]);
          setRoleDefaults([]);
          setOverrides([]);
          setSavedOverrides([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingCaps(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, user?.id, tenantId, consoleAssignments.length]);

  useDeferredEffect(() => {
    if (!open || !appId) {
      setRoles([]);
      setRoleDetails(new Map());
      return;
    }
    let cancelled = false;
    setLoadingRoles(true);
    void listRoles({ applicationId: appId })
      .then(async (rows) => {
        if (cancelled) return;
        setRoles(rows);
        const details = await Promise.all(rows.map((r) => getRole(r.id, appId, r)));
        if (!cancelled) setRoleDetails(new Map(details.map((d) => [d.id, d])));
      })
      .catch(() => {
        if (!cancelled) {
          setRoles([]);
          setRoleDetails(new Map());
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingRoles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, appId]);

  useDeferredEffect(() => {
    if (!open || !user || !appId) {
      setRoleIds([]);
      return;
    }
    const access = userApplicationAccess(user).find((a) => a.applicationId === appId);
    const ids = access?.roleIds ?? [];
    setRoleIds(ids);
    setSavedRoleIds(ids);
  }, [open, user, appId]);

  const previewEffectiveFeatures = useMemo(
    () => computeEffectiveFeatures(roleDefaults, overrides, catalogFeatures),
    [roleDefaults, overrides, catalogFeatures],
  );

  const rolesDirty = useMemo(() => {
    const saved = [...savedRoleIds].sort();
    const current = [...roleIds].sort();
    return JSON.stringify(saved) !== JSON.stringify(current);
  }, [savedRoleIds, roleIds]);

  const featuresDirty = useMemo(() => {
    const saved = [...savedOverrides].sort((a, b) => a.featureKey.localeCompare(b.featureKey));
    const current = [...overrides].sort((a, b) => a.featureKey.localeCompare(b.featureKey));
    return JSON.stringify(saved) !== JSON.stringify(current);
  }, [savedOverrides, overrides]);

  const tenantRolesDirty = useMemo(() => {
    const saved = [...savedTenantRoleIds].sort();
    const current = [...tenantRoleIds].sort();
    return JSON.stringify(saved) !== JSON.stringify(current);
  }, [savedTenantRoleIds, tenantRoleIds]);

  const grantPreviewFeatures = useMemo(
    () => consoleRoleCatalog[grantRoleType] ?? [],
    [consoleRoleCatalog, grantRoleType],
  );

  const effectivePermissions = useMemo(() => {
    const keys = new Set<string>();
    for (const id of roleIds) {
      const detail = roleDetails.get(id);
      if (!detail) continue;
      for (const p of detail.permissions) keys.add(p.key);
    }
    return [...keys].sort();
  }, [roleIds, roleDetails]);

  function overrideEffect(featureKey: string): "default" | "GRANT" | "DENY" {
    const row = overrides.find((o) => o.featureKey === featureKey);
    return row ? row.effect : "default";
  }

  function setOverride(featureKey: string, effect: "default" | "GRANT" | "DENY") {
    setOverrides((prev) => {
      const next = prev.filter((o) => o.featureKey !== featureKey);
      if (effect !== "default") next.push({ featureKey, effect });
      return next;
    });
  }

  function toggleRole(id: string) {
    setRoleIds((prev) =>
      prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id],
    );
  }

  function toggleTenantRole(id: string) {
    setTenantRoleIds((prev) =>
      prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id],
    );
  }

  async function saveTenantRoles() {
    if (!user) return;
    setSavingTenantRoles(true);
    try {
      const updated = await replaceUserTenantRoleAssignments(
        tenantId,
        user.id,
        tenantRoleIds,
      );
      setTenantRoleIds(updated.roleIds);
      setSavedTenantRoleIds(updated.roleIds);
      const caps = await fetchUserConsoleCapabilities(tenantId, user.id);
      setRoleDefaults(caps.roleDefaults);
      setOverrides(caps.overrides);
      setSavedOverrides(caps.overrides);
      toast("Tenant governance roles updated", "success");
      await onSaved({
        userId: user.id,
        tenantGovernanceRoleIds: updated.roleIds,
        tenantGovernanceRoleNames: updated.roleNames,
        effectiveConsoleFeatures: caps.effectiveFeatures,
        consoleFeatureOverrides: caps.overrides,
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update tenant roles", "error");
    } finally {
      setSavingTenantRoles(false);
    }
  }

  async function saveRoles() {
    if (!user || !appId) return;
    setSavingRoles(true);
    try {
      await updateUserApplicationRoles(user.id, appId, tenantId, roleIds);
      setSavedRoleIds(roleIds);
      toast("In-app roles updated", "success");
      const access = userApplicationAccess(user).map((row) =>
        row.applicationId === appId
          ? {
              ...row,
              roleIds,
              roleNames: roles.filter((r) => roleIds.includes(r.id)).map((r) => r.name),
              permissionKeys: effectivePermissions,
            }
          : row,
      );
      const roleNames = [
        ...new Set(access.flatMap((row) => row.roleNames)),
      ].sort();
      await onSaved({
        userId: user.id,
        applicationAccess: access,
        roleNames,
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update roles", "error");
    } finally {
      setSavingRoles(false);
    }
  }

  async function saveFeatures() {
    if (!user) return;
    setSavingCaps(true);
    try {
      const updated = await updateUserConsoleCapabilities(tenantId, user.id, overrides);
      setOverrides(updated.overrides);
      setSavedOverrides(updated.overrides);
      toast("Console features updated", "success");
      await onSaved({
        userId: user.id,
        effectiveConsoleFeatures: updated.effectiveFeatures,
        consoleFeatureOverrides: updated.overrides,
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update features", "error");
    } finally {
      setSavingCaps(false);
    }
  }

  async function grantAccess() {
    if (!user) return;
    if (needsGrantApp && !grantAppId) {
      toast("Select an application", "error");
      return;
    }
    setGranting(true);
    try {
      await grantConsoleAccess(tenantId, {
        userId: user.id,
        roleType: grantRoleType,
        applicationId: needsGrantApp ? grantAppId : undefined,
      });
      toast("Console access granted", "success");
      await onSaved();
      setTab("features");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not grant access", "error");
    } finally {
      setGranting(false);
    }
  }

  async function revokeAssignment(assignmentId: string) {
    setRevokeBusy(assignmentId);
    try {
      await revokeConsoleAccess(tenantId, assignmentId);
      toast("Console access removed", "success");
      await onSaved();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not revoke access", "error");
    } finally {
      setRevokeBusy(null);
    }
  }

  if (!open || !user) return null;

  const tabs: { id: Tab; label: string }[] = [
    { id: "console", label: "Console access" },
    { id: "features", label: "Console features" },
    { id: "tenantRoles", label: "Tenant roles" },
    { id: "roles", label: "In-app RBAC" },
  ];

  return (
    <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8">
      <div
        role="dialog"
        aria-modal="true"
        className="relative my-8 w-full max-w-2xl rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
      >
        <div className="border-b border-ui px-5 py-4">
          <h2 className="text-base font-semibold text-ui">Manage operator access</h2>
          <p className="mt-0.5 text-xs text-muted">
            {user.displayName} · console access, tenant governance roles, and in-app permissions
          </p>
        </div>

        <div className="flex gap-1 border-b border-ui px-5 pt-3">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`rounded-t-lg px-3 py-2 text-xs font-medium transition-colors ${
                tab === t.id
                  ? "border border-b-0 border-ui bg-[var(--ui-surface)] text-ui"
                  : "text-muted hover:text-ui"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="max-h-[32rem] overflow-y-auto px-5 py-4">
          {tab === "console" && (
            <div className="space-y-4">
              <p className="text-xs text-muted">
                Tenant operators only — controls who can sign in to this admin console and which
                applications they manage. Custom tenant roles from Roles &amp; Permissions are
                assigned on the Tenant roles tab, not here.
              </p>
              {consoleAssignments.length === 0 ? (
                <div className="rounded-lg border border-dashed border-ui px-4 py-3">
                  <p className="text-sm text-faint">No console access yet.</p>
                </div>
              ) : (
                <ul className="divide-y divide-ui rounded-lg border border-ui">
                  {consoleAssignments.map((entry) => (
                    <li
                      key={entry.id}
                      className="flex items-center justify-between gap-3 px-4 py-3"
                    >
                      <div>
                        <Badge tone="info">{CONSOLE_ROLE_LABELS[entry.roleType]}</Badge>
                        <p className="mt-1 text-xs text-soft">
                          {entry.applicationName ??
                            (entry.roleType === "TENANT_SUPER_ADMIN"
                              ? "All applications"
                              : "—")}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={revokeBusy === entry.id}
                        onClick={() => void revokeAssignment(entry.id)}
                        className="text-xs font-medium text-red-600 hover:underline disabled:opacity-60"
                      >
                        {revokeBusy === entry.id ? "Removing…" : "Revoke"}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="rounded-lg border border-ui p-4">
                <p className="mb-3 text-xs font-medium text-ui">Grant console access</p>
                <div className="space-y-3">
                  <div>
                    <label className="mb-1 block text-xs text-muted">Role</label>
                    <Select
                      value={grantRoleType}
                      onChange={(e) =>
                        setGrantRoleType(e.target.value as AdminConsoleRoleType)
                      }
                      className="w-full text-sm"
                    >
                      <option value="APPLICATION_ADMIN">Application Admin</option>
                      <option value="TENANT_ADMIN">Tenant Admin</option>
                      <option value="TENANT_SUPER_ADMIN">Tenant Super Admin</option>
                    </Select>
                  </div>
                  {needsGrantApp && (
                    <div>
                      <label className="mb-1 block text-xs text-muted">Application scope</label>
                      <Select
                        value={grantAppId}
                        onChange={(e) => setGrantAppId(e.target.value)}
                        className="w-full text-sm"
                      >
                        {(memberApps.length > 0 ? memberApps : applications).map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.name}
                          </option>
                        ))}
                      </Select>
                    </div>
                  )}
                  <div className="rounded-md bg-ui-elevated px-3 py-2 text-[10px] text-soft">
                    Default features:{" "}
                    {grantPreviewFeatures
                      .map((f) => CONSOLE_FEATURE_LABELS[f] ?? f)
                      .join(", ") || "—"}
                  </div>
                  <button
                    type="button"
                    disabled={granting}
                    onClick={() => void grantAccess()}
                    className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand disabled:opacity-60"
                  >
                    {granting ? "Granting…" : "Grant console access"}
                  </button>
                </div>
              </div>
            </div>
          )}

          {tab === "features" && consoleAssignments.length === 0 && (
            <p className="text-sm text-faint">
              Grant console access first, then configure which admin console sections this operator
              can use.
            </p>
          )}

          {tab === "features" && consoleAssignments.length > 0 && (
            <div className="space-y-4">
              <p className="text-xs text-muted">
                Configure which admin console sections this operator can access. Role defaults apply
                first; grant or deny overrides adjust the effective matrix shown in the tenant roster.
              </p>
              {loadingCaps ? (
                <p className="text-sm text-muted">Loading feature matrix…</p>
              ) : (
                <>
                  <div className="flex flex-wrap items-center gap-2 rounded-lg border border-ui bg-ui-elevated px-3 py-2 text-xs">
                    <span className="font-medium text-ui">
                      {previewEffectiveFeatures.length} of {catalogFeatures.length} features allowed
                    </span>
                    {overrides.length > 0 && (
                      <span className="text-faint">
                        · {overrides.length} override{overrides.length === 1 ? "" : "s"}
                      </span>
                    )}
                    {featuresDirty && (
                      <Badge tone="warning">Unsaved changes</Badge>
                    )}
                  </div>
                  <div className="overflow-x-auto rounded-lg border border-ui">
                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-ui bg-ui-elevated text-faint">
                        <tr>
                          <th className="px-3 py-2 font-medium">Feature</th>
                          <th className="px-3 py-2 font-medium">Role default</th>
                          <th className="px-3 py-2 font-medium">Override</th>
                          <th className="px-3 py-2 font-medium">Effective</th>
                        </tr>
                      </thead>
                      <tbody>
                        {catalogFeatures.map((featureKey) => {
                          const inRole = roleDefaults.includes(featureKey);
                          const inEffective = previewEffectiveFeatures.includes(featureKey);
                          const effect = overrideEffect(featureKey);
                          return (
                            <tr key={featureKey} className="border-b border-ui last:border-0">
                              <td className="px-3 py-2 font-medium text-ui">
                                {CONSOLE_FEATURE_LABELS[featureKey] ?? featureKey}
                              </td>
                              <td className="px-3 py-2 text-soft">{inRole ? "Yes" : "No"}</td>
                              <td className="px-3 py-2">
                                <Select
                                  value={effect}
                                  onChange={(e) =>
                                    setOverride(
                                      featureKey,
                                      e.target.value as "default" | "GRANT" | "DENY",
                                    )
                                  }
                                  className="h-8 min-w-[7.5rem] text-xs"
                                >
                                  <option value="default">Default</option>
                                  <option value="GRANT">Grant</option>
                                  <option value="DENY">Deny</option>
                                </Select>
                              </td>
                              <td className="px-3 py-2">
                                <span
                                  className={
                                    inEffective
                                      ? "font-medium text-green-700 dark:text-green-400"
                                      : "text-faint"
                                  }
                                >
                                  {inEffective ? "Allowed" : "Denied"}
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  disabled={loadingCaps || savingCaps || !featuresDirty}
                  onClick={() => setOverrides(savedOverrides)}
                  className="text-xs font-medium text-muted hover:text-ui disabled:opacity-40"
                >
                  Reset changes
                </button>
                <button
                  type="button"
                  disabled={savingCaps || loadingCaps || !featuresDirty}
                  onClick={() => void saveFeatures()}
                  className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand disabled:opacity-60"
                >
                  {savingCaps ? "Saving…" : "Save feature overrides"}
                </button>
              </div>
            </div>
          )}

          {tab === "tenantRoles" && (
            <div className="space-y-4">
              <p className="text-xs text-muted">
                Assign custom tenant governance roles from Tenant → Roles &amp; Permissions.
                Console sections configured on those roles are applied automatically; per-user
                overrides remain available on the Features tab when console access is active.
              </p>
              {loadingTenantRoles ? (
                <p className="text-sm text-muted">Loading tenant roles…</p>
              ) : tenantGovernanceRoles.length === 0 ? (
                <div className="rounded-lg border border-dashed border-ui px-4 py-3">
                  <p className="text-sm text-faint">
                    No custom tenant roles yet. Create one from Tenant → Roles &amp; Permissions.
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  {tenantGovernanceRoles.map((r) => {
                    const selected = tenantRoleIds.includes(r.id);
                    return (
                      <label
                        key={r.id}
                        className={`flex cursor-pointer flex-col gap-1 rounded-lg border px-3 py-2 text-sm ${
                          selected ? "border-brand bg-brand-muted/30" : "border-ui"
                        }`}
                      >
                        <span className="flex items-center gap-2">
                          <input
                            type="checkbox"
                            checked={selected}
                            disabled={savingTenantRoles}
                            onChange={() => toggleTenantRole(r.id)}
                          />
                          <span>{r.name}</span>
                        </span>
                        {r.description && (
                          <span className="ml-6 text-xs text-faint">{r.description}</span>
                        )}
                      </label>
                    );
                  })}
                </div>
              )}
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  disabled={savingTenantRoles || !tenantRolesDirty}
                  onClick={() => setTenantRoleIds(savedTenantRoleIds)}
                  className="text-xs font-medium text-muted hover:text-ui disabled:opacity-40"
                >
                  Reset changes
                </button>
                <button
                  type="button"
                  disabled={
                    savingTenantRoles || !tenantRolesDirty || tenantGovernanceRoles.length === 0
                  }
                  onClick={() => void saveTenantRoles()}
                  className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand disabled:opacity-60"
                >
                  {savingTenantRoles ? "Saving…" : "Save tenant roles"}
                </button>
              </div>
            </div>
          )}

          {tab === "roles" && (
            <div className="space-y-4">
              {rolesDirty && (
                <div className="rounded-lg border border-ui bg-ui-elevated px-3 py-2">
                  <Badge tone="warning">Unsaved role changes</Badge>
                </div>
              )}
              <div>
                <label className="mb-1 block text-xs font-medium text-muted">Application</label>
                <Select
                  value={appId}
                  onChange={(e) => setAppId(e.target.value)}
                  className="w-full text-sm"
                >
                  {(memberApps.length > 0 ? memberApps : applications).map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                      {!user.applicationIds.includes(a.id) ? " (not a member)" : ""}
                    </option>
                  ))}
                </Select>
              </div>
              {loadingRoles ? (
                <p className="text-sm text-muted">Loading roles…</p>
              ) : (
                <div className="space-y-2">
                  {roles.map((r) => {
                    const detail = roleDetails.get(r.id);
                    const selected = roleIds.includes(r.id);
                    return (
                      <label
                        key={r.id}
                        className={`flex cursor-pointer flex-col gap-1.5 rounded-lg border px-3 py-2 text-sm ${
                          selected ? "border-brand bg-brand-muted/30" : "border-ui"
                        }`}
                      >
                        <span className="flex items-center gap-2">
                          <input
                            type="checkbox"
                            checked={selected}
                            disabled={!user.applicationIds.includes(appId) || savingRoles}
                            onChange={() => toggleRole(r.id)}
                          />
                          <span>{r.name}</span>
                        </span>
                        {selected && detail && detail.permissions.length > 0 && (
                          <div className="ml-6 flex flex-wrap gap-1">
                            {detail.permissions.map((p) => (
                              <span
                                key={p.id}
                                className="rounded-md bg-ui-elevated px-1.5 py-0.5 font-mono text-[10px] text-soft"
                              >
                                {p.key}
                              </span>
                            ))}
                          </div>
                        )}
                      </label>
                    );
                  })}
                </div>
              )}
              {effectivePermissions.length > 0 && (
                <p className="text-[10px] text-faint">
                  Effective: {effectivePermissions.join(", ")}
                </p>
              )}
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  disabled={savingRoles || !rolesDirty}
                  onClick={() => setRoleIds(savedRoleIds)}
                  className="text-xs font-medium text-muted hover:text-ui disabled:opacity-40"
                >
                  Reset changes
                </button>
                <button
                  type="button"
                  disabled={savingRoles || !rolesDirty || !user.applicationIds.includes(appId)}
                  onClick={() => void saveRoles()}
                  className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand disabled:opacity-60"
                >
                  {savingRoles ? "Saving…" : "Save in-app roles"}
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end border-t border-ui px-5 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-ui px-4 py-2 text-sm font-medium text-ui hover:bg-ui-elevated"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
