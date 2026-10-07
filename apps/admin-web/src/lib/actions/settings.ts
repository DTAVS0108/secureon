"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import {
  fetchAppearancePrefs,
  fetchEmailSettings,
  fetchNotificationSettings,
  fetchPlatformSmtp,
  saveAppearancePrefs,
  saveEmailSettings,
  saveNotificationSettings,
  type EmailSettings,
  type EmailTemplatesMap,
  type NotificationSettings,
  type PlatformNotificationSettings,
  type SmtpSettings,
  type TestEmailRequest,
} from "@/lib/api/settings";
import { ApiError } from "@/lib/api/http";
import { listAdminUsers, listUsers } from "@/lib/api/users";
import { listRoles } from "@/lib/api/roles";
import type { UiPreferences } from "@/lib/theme/types";

export type AdminRecipientOption = {
  id: string;
  email: string;
  label: string;
  roleNames: string[];
};

export type RecipientUserOption = {
  id: string;
  email: string;
  label: string;
};

function formatSettingsError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 404) {
      return "Auth server is running an old build — stop it and restart from apps/auth-server (./gradlew bootRun) so application SMTP/template APIs are available.";
    }
    return e.message;
  }
  if (e instanceof Error) return e.message;
  return "Request failed";
}

export async function loadAppearanceAction(): Promise<{
  prefs: Partial<UiPreferences>;
  error?: string;
}> {
  try {
    const prefs = await fetchAppearancePrefs();
    return { prefs };
  } catch (e) {
    return { prefs: {}, error: formatSettingsError(e) };
  }
}

export async function saveAppearanceAction(
  prefs: UiPreferences,
): Promise<{ ok: boolean; error?: string }> {
  try {
    await saveAppearancePrefs(prefs);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function loadAdminRecipientOptionsAction(): Promise<{
  adminOptions: AdminRecipientOption[];
  allUsers: RecipientUserOption[];
  error?: string;
}> {
  try {
    const [adminUsers, allAccounts, roles] = await Promise.all([
      listAdminUsers(),
      listUsers(),
      listRoles({}),
    ]);
    const roleNameById = new Map(roles.map((r) => [r.id, r.name]));
    const adminOptions: AdminRecipientOption[] = adminUsers.map((u) => {
      const name = `${u.firstName} ${u.lastName}`.trim();
      return {
        id: u.id,
        email: u.email.toLowerCase(),
        label: name || u.email,
        roleNames: u.roleIds
          .map((id) => roleNameById.get(id))
          .filter((n): n is string => Boolean(n)),
      };
    });
    const allUsers: RecipientUserOption[] = allAccounts
      .map((u) => {
        const name = `${u.firstName} ${u.lastName}`.trim();
        return {
          id: u.id,
          email: u.email.toLowerCase(),
          label: name || u.email,
        };
      })
      .sort((a, b) => a.label.localeCompare(b.label));
    return { adminOptions, allUsers };
  } catch (e) {
    return { adminOptions: [], allUsers: [], error: formatSettingsError(e) };
  }
}

export async function loadPlatformNotificationsAction(): Promise<{
  notifications: PlatformNotificationSettings;
  email: EmailSettings;
  smtp: SmtpSettings;
  error?: string;
}> {
  try {
    const [notifications, email, smtp] = await Promise.all([
      fetchNotificationSettings(),
      fetchEmailSettings(),
      fetchPlatformSmtp(),
    ]);
    return { notifications, email, smtp };
  } catch (e) {
    return { notifications: {}, email: {}, smtp: {}, error: formatSettingsError(e) };
  }
}

export async function savePlatformNotificationsAction(input: {
  notifications: PlatformNotificationSettings & { adminRecipients?: string[] };
  email: EmailSettings;
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const { smtpConfigured: _m, ...notifications } = input.notifications as PlatformNotificationSettings &
      Record<string, unknown>;
    const { smtpConfigured: _em, ...email } = input.email as EmailSettings & Record<string, unknown>;
    await saveNotificationSettings(notifications);
    await saveEmailSettings(email);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function savePlatformSmtpAction(
  body: SmtpSettings,
): Promise<{ ok: boolean; smtp?: SmtpSettings; error?: string }> {
  try {
    const { savePlatformSmtp } = await import("@/lib/api/settings");
    const smtp = await savePlatformSmtp(body);
    return { ok: true, smtp };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function loadNotificationsAction(applicationId: string): Promise<{
  notifications: NotificationSettings;
  email: EmailSettings;
  error?: string;
}> {
  try {
    const {
      fetchApplicationNotifications,
      fetchApplicationEmail,
    } = await import("@/lib/api/application-settings");
    const notifications = await fetchApplicationNotifications(applicationId);
    let email: EmailSettings = {};
    try {
      email = await fetchApplicationEmail(applicationId);
    } catch {
      email = {};
    }
    return { notifications, email };
  } catch (e) {
    return { notifications: {}, email: {}, error: formatSettingsError(e) };
  }
}

export async function loadApplicationSmtpAndTemplatesAction(applicationId: string): Promise<{
  smtp: SmtpSettings;
  templates: EmailTemplatesMap;
  emailTestUiEnabled: boolean;
  error?: string;
}> {
  const {
    fetchApplicationSmtp,
    fetchApplicationEmailTemplates,
    fetchApplicationFeatureFlags,
  } = await import("@/lib/api/application-settings");
  const { normalizeFeatureFlags } = await import("@/lib/auth-settings-normalize");

  let smtp: SmtpSettings = {};
  let templates: EmailTemplatesMap = {};
  let emailTestUiEnabled = true;
  const errors: string[] = [];

  try {
    smtp = await fetchApplicationSmtp(applicationId);
  } catch (e) {
    errors.push(formatSettingsError(e));
  }

  try {
    templates = await fetchApplicationEmailTemplates(applicationId);
  } catch (e) {
    errors.push(formatSettingsError(e));
  }

  try {
    const flags = normalizeFeatureFlags(await fetchApplicationFeatureFlags(applicationId));
    const testFlag = flags.find((f) => f.key === "notification_email_test_ui");
    emailTestUiEnabled = testFlag ? testFlag.enabled : true;
  } catch {
    emailTestUiEnabled = true;
  }

  return {
    smtp,
    templates,
    emailTestUiEnabled,
    error: errors.length ? errors.join(" ") : undefined,
  };
}

export async function saveSmtpAction(
  applicationId: string,
  body: SmtpSettings,
): Promise<{ ok: boolean; smtp?: SmtpSettings; error?: string }> {
  try {
    const { saveApplicationSmtp } = await import("@/lib/api/application-settings");
    const smtp = await saveApplicationSmtp(applicationId, body);
    return { ok: true, smtp };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function saveEmailTemplatesAction(
  applicationId: string,
  body: EmailTemplatesMap,
): Promise<{ ok: boolean; templates?: EmailTemplatesMap; error?: string }> {
  try {
    const { saveApplicationEmailTemplates } = await import("@/lib/api/application-settings");
    const templates = await saveApplicationEmailTemplates(applicationId, body);
    return { ok: true, templates };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function loadEmailTemplateDefaultsAction(
  applicationId: string,
): Promise<{ ok: boolean; templates?: EmailTemplatesMap; error?: string }> {
  try {
    const { fetchApplicationEmailTemplateDefaults } = await import("@/lib/api/application-settings");
    const templates = await fetchApplicationEmailTemplateDefaults(applicationId);
    return { ok: true, templates };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function resetEmailTemplateAction(
  applicationId: string,
  templateKey: string,
): Promise<{ ok: boolean; templates?: EmailTemplatesMap; error?: string }> {
  try {
    const { resetApplicationEmailTemplate } = await import("@/lib/api/application-settings");
    const templates = await resetApplicationEmailTemplate(applicationId, templateKey);
    return { ok: true, templates };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function sendTestEmailAction(
  applicationId: string,
  body: TestEmailRequest,
): Promise<{ ok: boolean; error?: string }> {
  try {
    const { sendApplicationTestEmail } = await import("@/lib/api/application-settings");
    await sendApplicationTestEmail(applicationId, body);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function saveNotificationsAction(
  input: {
    notifications: NotificationSettings & { adminRecipients?: string[] };
    email: EmailSettings;
  },
  applicationId: string,
  sections?: { saveNotifications?: boolean; saveEmail?: boolean },
): Promise<{ ok: boolean; error?: string }> {
  try {
    const {
      saveApplicationNotifications,
      saveApplicationEmail,
    } = await import("@/lib/api/application-settings");
    const { scope: _s, smtpConfigured: _m, ...notifications } =
      input.notifications as NotificationSettings & Record<string, unknown>;
    const { scope: _es, smtpConfigured: _em, ...email } =
      input.email as EmailSettings & Record<string, unknown>;
    const saveNotif = sections?.saveNotifications !== false;
    const saveMail = sections?.saveEmail !== false;
    if (saveNotif) {
      await saveApplicationNotifications(applicationId, notifications);
    }
    if (saveMail) {
      try {
        await saveApplicationEmail(applicationId, email);
      } catch (e) {
        if (!(e instanceof ApiError) || e.status !== 404) throw e;
      }
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}

export async function resetApplicationNotificationsAction(
  applicationId: string,
): Promise<{ ok: boolean; error?: string }> {
  try {
    const {
      resetApplicationNotifications,
      resetApplicationEmail,
    } = await import("@/lib/api/application-settings");
    await Promise.all([
      resetApplicationNotifications(applicationId),
      resetApplicationEmail(applicationId),
    ]);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatSettingsError(e) };
  }
}
