"use client";

import { AdminRecipientsPicker } from "@/components/settings/AdminRecipientsPicker";
import { SmtpSettingsCard } from "@/components/settings/SmtpSettingsCard";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { Toggle } from "@/components/ui/Toggle";
import {
  loadAdminRecipientOptionsAction,
  loadPlatformNotificationsAction,
  savePlatformNotificationsAction,
} from "@/lib/actions/settings";
import type { AdminRecipientOption, RecipientUserOption } from "@/lib/actions/settings";
import type { EmailSettings, NotificationSettings, SmtpSettings } from "@/lib/api/settings";
import { useCallback, useEffect, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

export type PlatformNotificationSettings = Pick<
  NotificationSettings,
  "emailEnabled" | "auditAlertsEnabled" | "securityAlertsEnabled" | "smtpConfigured"
>;

function serializeDraft(
  notifications: PlatformNotificationSettings,
  email: EmailSettings,
  selectedEmails: string[],
): string {
  return JSON.stringify({
    notifications: {
      emailEnabled: notifications.emailEnabled,
      auditAlertsEnabled: notifications.auditAlertsEnabled,
      securityAlertsEnabled: notifications.securityAlertsEnabled,
      adminRecipients: [...selectedEmails].sort(),
    },
    email: {
      fromName: email.fromName ?? "",
      fromAddress: email.fromAddress ?? "",
      replyTo: email.replyTo ?? "",
    },
  });
}

export function PlatformNotificationsSettings() {
  const { toast } = useToast();
  const toastRef = useRef(toast);
  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);
  const [notifications, setNotifications] = useState<PlatformNotificationSettings>({});
  const [email, setEmail] = useState<EmailSettings>({});
  const [smtp, setSmtp] = useState<SmtpSettings>({});
  const [selectedEmails, setSelectedEmails] = useState<string[]>([]);
  const [adminOptions, setAdminOptions] = useState<AdminRecipientOption[]>([]);
  const [allUsers, setAllUsers] = useState<RecipientUserOption[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const baselineSnapshotRef = useRef<string | null>(null);
  const notificationsRef = useRef(notifications);
  const emailRef = useRef(email);
  const selectedEmailsRef = useRef(selectedEmails);
  useEffect(() => {
    notificationsRef.current = notifications;
    emailRef.current = email;
    selectedEmailsRef.current = selectedEmails;
  }, [notifications, email, selectedEmails]);

  useDeferredEffect(() => {
    let cancelled = false;

    async function load() {
      const [{ notifications: n, email: e, smtp: s, error }, recipients] = await Promise.all([
        loadPlatformNotificationsAction(),
        loadAdminRecipientOptionsAction(),
      ]);
      if (cancelled) return;

      const adminRecipients = (n.adminRecipients ?? []).map((x) => x.trim().toLowerCase());
      setNotifications({
        emailEnabled: n.emailEnabled,
        auditAlertsEnabled: n.auditAlertsEnabled,
        securityAlertsEnabled: n.securityAlertsEnabled,
        smtpConfigured: n.smtpConfigured ?? s.smtpConfigured,
      });
      setEmail(e);
      setSmtp(s);
      setSelectedEmails(adminRecipients);
      setAdminOptions(recipients.adminOptions);
      setAllUsers(recipients.allUsers);
      baselineSnapshotRef.current = serializeDraft(
        {
          emailEnabled: n.emailEnabled,
          auditAlertsEnabled: n.auditAlertsEnabled,
          securityAlertsEnabled: n.securityAlertsEnabled,
          smtpConfigured: n.smtpConfigured ?? s.smtpConfigured,
        },
        e,
        adminRecipients,
      );
      if (error || recipients.error) {
        const msg = [error, recipients.error].filter(Boolean).join(" ");
        setMessage(msg);
        toastRef.current(msg, "error");
      }
      setLoaded(true);
    }

    baselineSnapshotRef.current = null;
    setLoaded(false);
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const persist = useCallback(async () => {
    setSaving(true);
    const result = await savePlatformNotificationsAction({
      notifications: {
        ...notificationsRef.current,
        adminRecipients: selectedEmailsRef.current,
      },
      email: emailRef.current,
    });
    if (result.ok) {
      const nextNotifications = {
        ...notificationsRef.current,
        adminRecipients: selectedEmailsRef.current,
      };
      baselineSnapshotRef.current = serializeDraft(
        notificationsRef.current,
        emailRef.current,
        selectedEmailsRef.current,
      );
      toastRef.current("Platform alert settings saved", "success");
      void nextNotifications;
    } else {
      const msg = result.error ?? "Save failed";
      setMessage(msg);
      toastRef.current(msg, "error");
    }
    setSaving(false);
  }, []);

  useDeferredEffect(() => {
    if (!loaded || baselineSnapshotRef.current === null) return;
    const snapshot = serializeDraft(notifications, email, selectedEmails);
    if (snapshot === baselineSnapshotRef.current) return;
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      persist();
    }, 800);
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [notifications, email, selectedEmails, loaded, persist]);

  const smtpReady = notifications.smtpConfigured || smtp.smtpConfigured || smtp.passwordConfigured;

  return (
    <div className="space-y-6">
      {message && (
        <p className="rounded-lg bg-brand-muted px-3 py-2 text-sm text-brand">{message}</p>
      )}

      <Card padded={false}>
        <CardHeader
          title="Platform alert channels"
          description={
            smtpReady
              ? "Email alerts for platform operator events (tenants, applications, high-risk admin actions)."
              : "Configure platform SMTP below to deliver operator alert emails."
          }
        />
        <ul className={`divide-y divide-ui ${!loaded ? "pointer-events-none opacity-60" : ""}`}>
          <SettingRow
            label="Email alerts"
            hint="Master switch for platform operator notifications"
            checked={!!notifications.emailEnabled}
            onChange={(v) => setNotifications({ ...notifications, emailEnabled: v })}
          />
          <SettingRow
            label="Audit alerts"
            hint="Tenant and application lifecycle events"
            checked={!!notifications.auditAlertsEnabled}
            onChange={(v) => setNotifications({ ...notifications, auditAlertsEnabled: v })}
          />
          <SettingRow
            label="Security alerts"
            hint="Platform-level security events when no application context is available"
            checked={!!notifications.securityAlertsEnabled}
            onChange={(v) => setNotifications({ ...notifications, securityAlertsEnabled: v })}
          />
        </ul>
        <div className="space-y-4 border-t border-ui p-5">
          <FieldRow label="Platform admin recipients" hint="Operators who receive platform alert emails">
            <AdminRecipientsPicker
              adminOptions={adminOptions}
              allUsers={allUsers}
              selectedEmails={selectedEmails}
              onChange={setSelectedEmails}
              disabled={!loaded}
            />
          </FieldRow>
        </div>
        {saving && <p className="px-5 pb-4 text-xs text-muted">Saving…</p>}
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Platform email sender"
          description="From address shown on platform operator alert emails"
        />
        <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
          <FieldRow label="From name">
            <Input
              value={email.fromName ?? ""}
              onChange={(e) => setEmail({ ...email, fromName: e.target.value })}
            />
          </FieldRow>
          <FieldRow label="From address">
            <Input
              value={email.fromAddress ?? ""}
              onChange={(e) => setEmail({ ...email, fromAddress: e.target.value })}
            />
          </FieldRow>
          <FieldRow label="Reply-to">
            <Input
              value={email.replyTo ?? ""}
              onChange={(e) => setEmail({ ...email, replyTo: e.target.value })}
            />
          </FieldRow>
        </div>
      </Card>

      <SmtpSettingsCard
        scope="platform"
        smtp={smtp}
        onSaved={(next) => {
          setSmtp(next);
          setNotifications((prev) => ({ ...prev, smtpConfigured: next.smtpConfigured }));
        }}
      />
    </div>
  );
}

function SettingRow({
  label,
  hint,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  hint: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <li className="flex items-center justify-between gap-4 px-5 py-4">
      <div>
        <p className="text-sm font-medium text-[var(--ui-text)]">{label}</p>
        <p className="text-xs text-muted">{hint}</p>
      </div>
      <Toggle checked={checked} disabled={disabled} onChange={onChange} aria-label={label} />
    </li>
  );
}
