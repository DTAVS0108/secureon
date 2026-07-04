"use client";

import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { OperatorSecurityPanel } from "@/components/profile/OperatorSecurityPanel";
import {
  changeOperatorPassword,
  fetchOperatorProfile,
  updateOperatorProfile,
  type OperatorProfile,
} from "@/lib/api/profile";
import { statusTone } from "@/lib/status";
import { formatDate } from "@/lib/format";
import type { Status } from "@/lib/types";

export function OperatorProfilePanel({
  initial,
  variant = "page",
}: {
  initial?: OperatorProfile | null;
  variant?: "page" | "modal";
}) {
  const { toast } = useToast();
  const [profile, setProfile] = useState<OperatorProfile | null>(initial ?? null);
  const [username, setUsername] = useState(initial?.username ?? "");
  const [displayName, setDisplayName] = useState(initial?.displayName ?? "");
  const [loading, setLoading] = useState(!initial);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  useDeferredEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchOperatorProfile()
      .then((p) => {
        if (cancelled) return;
        setProfile(p);
        setUsername(p.username ?? "");
        setDisplayName(p.displayName);
        setLoadError(null);
      })
      .catch((e) => {
        if (cancelled) return;
        setLoadError(e instanceof Error ? e.message : "Could not load profile.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading && !profile) {
    return <p className="text-sm text-muted">Loading profile…</p>;
  }

  if (!profile) {
    return (
      <p className="text-sm text-amber-800 dark:text-amber-200">
        {loadError ?? "Could not load profile."}
      </p>
    );
  }

  const readOnly = profile.platformSuperAdmin;

  async function saveProfile() {
    setSaving(true);
    try {
      const updated = await updateOperatorProfile({
        username: username.trim() || undefined,
        displayName: displayName.trim() || undefined,
      });
      setProfile(updated);
      toast("Profile updated", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update profile", "error");
    } finally {
      setSaving(false);
    }
  }

  async function submitPasswordChange() {
    if (newPassword !== confirmPassword) {
      toast("New passwords do not match", "error");
      return;
    }
    setChangingPassword(true);
    try {
      await changeOperatorPassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      toast("Password changed", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not change password", "error");
    } finally {
      setChangingPassword(false);
    }
  }

  const accountSection = (
    <>
      <div className={variant === "modal" ? "space-y-4" : "space-y-4 px-5 pb-5"}>
          <div className="flex flex-wrap gap-2">
            <Badge tone="neutral" className="capitalize">
              {profile.operatorTier.replace("_", " ")}
            </Badge>
            {profile.platformSuperAdmin && <Badge tone="success">Super admin</Badge>}
            <Badge tone={statusTone(profile.status as Status)} dot className="capitalize">
              {profile.status}
            </Badge>
          </div>

          {profile.tenantName && (
            <p className="text-sm text-soft">
              Organization: <span className="font-medium text-ui">{profile.tenantName}</span>
              {profile.tenantSlug ? ` (@${profile.tenantSlug})` : ""}
            </p>
          )}

          <FieldRow label="Email">
            <Input value={profile.email ?? profile.principal} readOnly disabled />
          </FieldRow>

          <FieldRow label="Username">
            <Input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              readOnly={readOnly}
              disabled={readOnly}
            />
          </FieldRow>

          <FieldRow label="Display name">
            <Input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              readOnly={readOnly}
              disabled={readOnly}
            />
          </FieldRow>

          {profile.createdAt && (
            <p className="text-xs text-faint">Member since {formatDate(profile.createdAt)}</p>
          )}

        {!readOnly && (
          <Button onClick={() => void saveProfile()} disabled={saving}>
            {saving ? "Saving…" : "Save changes"}
          </Button>
        )}
      </div>
    </>
  );

  const passwordDescription = readOnly
    ? "Change the platform admin password in SECUREONE_DEV_PASSWORD or your deployment secrets."
    : profile.hasPassword
      ? "Enter your current password, then choose a new one."
      : "Set a password for console sign-in.";

  const passwordSection = (
    <div className={variant === "modal" ? "space-y-4" : "space-y-4 px-5 pb-5"}>
          {readOnly ? (
            <p className="text-sm text-soft">
              Dev login: <code className="rounded bg-ui-elevated px-1.5 py-0.5">admin</code> /{" "}
              <code className="rounded bg-ui-elevated px-1.5 py-0.5">admin</code> (or your
              configured SECUREONE_DEV_PASSWORD).
            </p>
          ) : (
            <>
              <FieldRow label="Current password">
                <Input
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </FieldRow>
              <FieldRow label="New password">
                <Input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                />
              </FieldRow>
              <FieldRow label="Confirm new password">
                <Input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                />
              </FieldRow>
              <Button
                variant="secondary"
                onClick={() => void submitPasswordChange()}
                disabled={changingPassword || !currentPassword || !newPassword}
              >
                {changingPassword ? "Updating…" : "Change password"}
              </Button>
          </>
        )}
    </div>
  );

  if (variant === "modal") {
    return (
      <div className="space-y-6">
        <section>
          <h3 className="text-sm font-medium text-ui">Account</h3>
          <p className="mt-0.5 text-xs text-muted">
            {readOnly
              ? "Platform super-admin credentials are managed via server configuration."
              : "Update how your name appears in the admin console."}
          </p>
          <div className="mt-4">{accountSection}</div>
        </section>
        <section className="border-t border-ui pt-6">
          <h3 className="text-sm font-medium text-ui">Password</h3>
          <p className="mt-0.5 text-xs text-muted">{passwordDescription}</p>
          <div className="mt-4">{passwordSection}</div>
        </section>
        <section className="border-t border-ui pt-6">
          <h3 className="text-sm font-medium text-ui">Multi-factor authentication</h3>
          <p className="mt-0.5 text-xs text-muted">Enroll TOTP or passkeys for your tenant user account.</p>
          <div className="mt-4">
            <OperatorSecurityPanel platformSuperAdmin={readOnly} variant="modal" />
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="grid max-w-4xl grid-cols-1 gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader
          title="Account"
          description={
            readOnly
              ? "Platform super-admin credentials are managed via server configuration."
              : "Update how your name appears in the admin console."
          }
        />
        {accountSection}
      </Card>
      <Card>
        <CardHeader title="Password" description={passwordDescription} />
        {passwordSection}
      </Card>
      <Card className="lg:col-span-2">
        <CardHeader
          title="Multi-factor authentication"
          description="Enroll TOTP or passkeys for your tenant user account."
        />
        <OperatorSecurityPanel platformSuperAdmin={readOnly} variant="page" />
      </Card>
    </div>
  );
}
