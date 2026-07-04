"use client";

import { AdminRecipientsPicker } from "@/components/settings/AdminRecipientsPicker";
import { EmailTemplatesSettings } from "@/components/settings/EmailTemplatesSettings";
import { EmailTestPanel } from "@/components/settings/EmailTestPanel";
import { RecipientGroupsEditor } from "@/components/settings/RecipientGroupsEditor";
import { SmtpSettingsCard } from "@/components/settings/SmtpSettingsCard";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { Toggle } from "@/components/ui/Toggle";
import {
  loadApplicationSmtpAndTemplatesAction,
  loadNotificationsAction,
  saveNotificationsAction,
  type AdminRecipientOption,
  type RecipientUserOption,
} from "@/lib/actions/settings";
import type {
  EmailSettings,
  EmailTemplatesMap,
  NotificationSettings,
  RecipientGroups,
  SmtpSettings,
} from "@/lib/api/settings";
import {
  getCachedNotificationsBundle,
  loadNotificationsBundleDeduped,
  patchCachedNotificationsBundle,
  type NotificationsBundle,
} from "@/lib/settings-notifications-cache";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

const EMPTY_RECIPIENT_GROUPS: RecipientGroups = { security: [], operations: [] };

function normalizeEmailFields(email: EmailSettings): EmailSettings {
  return {
    fromName: email.fromName ?? "",
    fromAddress: email.fromAddress ?? "",
    replyTo: email.replyTo ?? "",
  };
}

function serializeNotificationsDraft(
  notifications: NotificationSettings,
  email: EmailSettings,
  selectedEmails: string[],
): string {
  const payload = {
    ...notifications,
    adminRecipients: [...selectedEmails].sort(),
  } as Record<string, unknown>;
  delete payload.inheritsPlatformDefaults;
  delete payload.scope;
  return JSON.stringify({
    notifications: payload,
    email: normalizeEmailFields(email),
  });
}

function applyBundle(bundle: NotificationsBundle) {
  return {
    notifications: bundle.notifications,
    email: bundle.email,
    smtp: bundle.smtp,
    templates: bundle.templates,
    emailTestUiEnabled: bundle.emailTestUiEnabled,
    adminRecipients: bundle.adminRecipients,
    adminOptions: bundle.adminOptions,
    allUsers: bundle.allUsers,
    errors: bundle.errors,
    baseline: serializeNotificationsDraft(bundle.notifications, bundle.email, bundle.adminRecipients),
  };
}

export function NotificationsSettings({
  applicationId,
  settingsReady = true,
}: {
  applicationId: string;
  /** When false (app exposure still loading), block edits but keep mounted to avoid reload loops. */
  settingsReady?: boolean;
}) {
  const { toast } = useToast();
  const toastRef = useRef(toast);
  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);
  const [notifications, setNotifications] = useState<NotificationSettings>({});
  const [email, setEmail] = useState<EmailSettings>({});
  const [smtp, setSmtp] = useState<SmtpSettings>({});
  const [templates, setTemplates] = useState<EmailTemplatesMap>({});
  const [emailTestUiEnabled, setEmailTestUiEnabled] = useState(false);
  const [selectedEmails, setSelectedEmails] = useState<string[]>([]);
  const [adminOptions, setAdminOptions] = useState<AdminRecipientOption[]>([]);
  const [allUsers, setAllUsers] = useState<RecipientUserOption[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const baselineSnapshotRef = useRef<string | null>(null);
  const loadGenerationRef = useRef(0);
  const notificationsRef = useRef(notifications);
  const emailRef = useRef(email);
  const selectedEmailsRef = useRef(selectedEmails);
  useEffect(() => {
    notificationsRef.current = notifications;
    emailRef.current = email;
    selectedEmailsRef.current = selectedEmails;
  }, [notifications, email, selectedEmails]);

  const recipientGroups = useMemo(
    () => notifications.recipientGroups ?? EMPTY_RECIPIENT_GROUPS,
    [notifications.recipientGroups],
  );

  useDeferredEffect(() => {
    const loadId = ++loadGenerationRef.current;
    let cancelled = false;

    const cached = getCachedNotificationsBundle(applicationId);
    if (cached) {
      const applied = applyBundle(cached);
      setNotifications(applied.notifications);
      setEmail(applied.email);
      setSmtp(applied.smtp);
      setTemplates(applied.templates);
      setEmailTestUiEnabled(applied.emailTestUiEnabled);
      setSelectedEmails(applied.adminRecipients);
      setAdminOptions(applied.adminOptions);
      setAllUsers(applied.allUsers);
      baselineSnapshotRef.current = applied.baseline;
      if (applied.errors) setMessage(applied.errors);
      setLoaded(true);
      return;
    }

    async function fetchBundle(): Promise<NotificationsBundle> {
      const { ensureApplicationPolicyScopes } = await import("@/lib/api/ensure-application-policy");
      await ensureApplicationPolicyScopes(applicationId, ["notifications", "email"]);

      const [
        { notifications: n, email: e, error },
        { smtp: smtpLoaded, templates: templatesLoaded, emailTestUiEnabled: testUi, error: smtpError },
      ] = await Promise.all([
        loadNotificationsAction(applicationId),
        loadApplicationSmtpAndTemplatesAction(applicationId),
      ]);

      const adminRecipients = (n.adminRecipients ?? []).map((x) => x.trim().toLowerCase());
      return {
        notifications: n,
        email: e,
        smtp: smtpLoaded,
        templates: templatesLoaded,
        emailTestUiEnabled: testUi,
        adminOptions: [],
        allUsers: [],
        adminRecipients,
        errors: [error, smtpError].filter(Boolean).join(" "),
      };
    }

    async function load() {
      try {
        const bundle = await loadNotificationsBundleDeduped(applicationId, fetchBundle);
        if (cancelled || loadId !== loadGenerationRef.current) return;

        const applied = applyBundle(bundle);
        setNotifications(applied.notifications);
        setEmail(applied.email);
        setSmtp(applied.smtp);
        setTemplates(applied.templates);
        setEmailTestUiEnabled(applied.emailTestUiEnabled);
        setSelectedEmails(applied.adminRecipients);
        setAdminOptions(applied.adminOptions);
        setAllUsers(applied.allUsers);
        baselineSnapshotRef.current = applied.baseline;
        if (applied.errors) {
          setMessage(applied.errors);
          toastRef.current(applied.errors, "error");
        }
        setLoaded(true);
      } catch (e) {
        if (!cancelled && loadId === loadGenerationRef.current) {
          toastRef.current(e instanceof Error ? e.message : "Failed to prepare settings", "error");
        }
      }
    }

    baselineSnapshotRef.current = null;
    setLoaded(false);
    load();
    return () => {
      cancelled = true;
    };
  }, [applicationId]);

  const persist = useCallback(async () => {
    setSaving(true);
    const result = await saveNotificationsAction(
      {
        notifications: { ...notificationsRef.current, adminRecipients: selectedEmailsRef.current },
        email: emailRef.current,
      },
      applicationId,
      { saveNotifications: true, saveEmail: true },
    );
    if (result.ok) {
      const nextNotifications = { ...notificationsRef.current, adminRecipients: selectedEmailsRef.current };
      const nextEmail = emailRef.current;
      baselineSnapshotRef.current = serializeNotificationsDraft(
        nextNotifications,
        nextEmail,
        selectedEmailsRef.current,
      );
      patchCachedNotificationsBundle(applicationId, {
        notifications: nextNotifications,
        email: nextEmail,
      });
      toastRef.current("Settings saved", "success");
    } else {
      const msg = result.error ?? "Save failed";
      setMessage(msg);
      toastRef.current(msg, "error");
    }
    setSaving(false);
  }, [applicationId]);

  useDeferredEffect(() => {
    if (!loaded || !settingsReady || baselineSnapshotRef.current === null) return;
    const snapshot = serializeNotificationsDraft(notifications, email, selectedEmails);
    if (snapshot === baselineSnapshotRef.current) return;
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      persist();
    }, 800);
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [notifications, email, selectedEmails, loaded, settingsReady, persist]);

  const smtpReady = notifications.smtpConfigured || smtp.smtpConfigured || smtp.passwordConfigured;

  return (
    <div className="space-y-6">
      {message && (
        <p className="rounded-lg bg-brand-muted px-3 py-2 text-sm text-brand">{message}</p>
      )}

      <Card padded={false}>
        <CardHeader
          title="Notification channels"
          description={
            smtpReady
              ? "Application email and alert settings. Changes auto-save."
              : "Configure SMTP below. Gmail: smtp.gmail.com, port 465, SSL + App Password."
          }
        />
        <ul
          className={`divide-y divide-ui ${!loaded || !settingsReady ? "pointer-events-none opacity-60" : ""}`}
        >
          <SettingRow
            label="Email notifications (admin)"
            hint="Application admin alerts to recipients below"
            checked={!!notifications.emailEnabled}
            onChange={(v) => setNotifications({ ...notifications, emailEnabled: v })}
          />
          <SettingRow
            label="User email (transactional)"
            hint="Verify email, password reset, and password-changed messages to end users"
            checked={notifications.userEmailEnabled !== false}
            onChange={(v) => setNotifications({ ...notifications, userEmailEnabled: v })}
          />
          <SettingRow
            label="Push notifications"
            hint="Mobile / web push (coming soon)"
            checked={!!notifications.pushEnabled}
            onChange={(v) => setNotifications({ ...notifications, pushEnabled: v })}
          />
          <SettingRow
            label="Audit alerts"
            hint="Email on high-risk admin actions"
            checked={!!notifications.auditAlertsEnabled}
            onChange={(v) => setNotifications({ ...notifications, auditAlertsEnabled: v })}
          />
          <SettingRow
            label="Security alerts"
            hint="Failed logins, lockouts, MFA changes"
            checked={!!notifications.securityAlertsEnabled}
            onChange={(v) => setNotifications({ ...notifications, securityAlertsEnabled: v })}
          />
        </ul>
        <div className="space-y-4 border-t border-ui p-5">
          <FieldRow label="Admin recipients" hint="Expand to pick admin users or add any email">
            <AdminRecipientsPicker
              adminOptions={adminOptions}
              allUsers={allUsers}
              selectedEmails={selectedEmails}
              onChange={setSelectedEmails}
              disabled={!loaded || !settingsReady}
            />
          </FieldRow>
          <FieldRow label="Recipient groups" hint="Named lists merged into admin alert delivery">
            <RecipientGroupsEditor
              groups={recipientGroups}
              onChange={(nextGroups) =>
                setNotifications((prev) => ({ ...prev, recipientGroups: nextGroups }))
              }
              disabled={!loaded || !settingsReady}
            />
          </FieldRow>
        </div>
        {saving && <p className="px-5 pb-4 text-xs text-muted">Saving…</p>}
      </Card>

      <Card padded={false}>
        <CardHeader title="Email sender" description="From address shown on outbound mail" />
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
        applicationId={applicationId}
        smtp={smtp}
        onSaved={(next) => {
          setSmtp(next);
          setNotifications((prev) => {
            const updated = { ...prev, smtpConfigured: next.smtpConfigured };
            patchCachedNotificationsBundle(applicationId, { smtp: next, notifications: updated });
            return updated;
          });
        }}
      />
      <EmailTemplatesSettings
        applicationId={applicationId}
        templates={templates}
        recipientGroups={recipientGroups}
        adminOptions={adminOptions}
        allUsers={allUsers}
        onSaved={(next) => {
          setTemplates(next);
          patchCachedNotificationsBundle(applicationId, { templates: next });
        }}
      />
      {emailTestUiEnabled && (
        <EmailTestPanel
          applicationId={applicationId}
          templates={templates}
          recipientGroups={recipientGroups}
          adminOptions={adminOptions}
          allUsers={allUsers}
        />
      )}
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
