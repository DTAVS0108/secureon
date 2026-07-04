"use client";

import type { SettingsScope } from "@/components/settings/SettingsTabs";

export function SettingsScopeBanner({ scope }: { scope: SettingsScope }) {
  if (scope.mode === "platform") {
    return (
      <div className="mb-6 rounded-lg border border-ui bg-surface px-4 py-3 text-sm text-muted">
        <strong className="text-ui">Platform settings</strong> configure operator-level policies.
        <strong className="text-ui"> Notifications</strong> here are platform operator alerts only.
        Application email, SMTP, and templates are configured separately under{" "}
        <strong>Application → Settings → Notifications</strong>.
      </div>
    );
  }
  return null;
}
