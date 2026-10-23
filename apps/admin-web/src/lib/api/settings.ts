import { browserApiFetch as apiFetch } from "./browser-client";
import type { UiPreferences } from "@/lib/theme/types";

export async function fetchAppearancePrefs(): Promise<Partial<UiPreferences>> {
  const data = await apiFetch<Partial<UiPreferences>>("/api/admin/v1/settings/appearance");
  return data ?? {};
}

export async function saveAppearancePrefs(prefs: UiPreferences): Promise<void> {
  await apiFetch("/api/admin/v1/settings/appearance", {
    method: "PUT",
    body: JSON.stringify(prefs),
  });
}

export type RecipientGroups = Record<string, string[]>;

export type PlatformNotificationSettings = {
  emailEnabled?: boolean;
  auditAlertsEnabled?: boolean;
  securityAlertsEnabled?: boolean;
  adminRecipients?: string[];
  smtpConfigured?: boolean;
};

export type NotificationSettings = {
  emailEnabled?: boolean;
  /** End-user mail: verify email, password reset, password changed */
  userEmailEnabled?: boolean;
  pushEnabled?: boolean;
  auditAlertsEnabled?: boolean;
  securityAlertsEnabled?: boolean;
  adminRecipients?: string[];
  recipientGroups?: RecipientGroups;
  smtpConfigured?: boolean;
};

export type EmailSettings = {
  fromName?: string;
  fromAddress?: string;
  replyTo?: string;
  smtpConfigured?: boolean;
};

export type SmtpSecurity = "ssl" | "starttls" | "none";

export type SmtpSettings = {
  host?: string;
  port?: number;
  security?: SmtpSecurity;
  username?: string;
  password?: string;
  authEnabled?: boolean;
  passwordConfigured?: boolean;
  smtpConfigured?: boolean;
};

export type EmailTemplate = {
  name?: string;
  description?: string;
  subject?: string;
  bodyText?: string;
  bodyHtml?: string;
  cc?: string[];
  bcc?: string[];
  enabled?: boolean;
  variables?: string[];
  /** Server: carries a reset/sign-in link or a code, so it goes to the user only (no cc or bcc). */
  carriesSecret?: boolean;
  /** Server: a security notice that is always sent (it cannot be switched off). */
  alwaysSent?: boolean;
};

export type EmailTemplatesMap = Record<string, EmailTemplate>;

export type TestEmailRequest = {
  to: string;
  cc?: string[];
  bcc?: string[];
  templateKey?: string;
  customData?: Record<string, string>;
};

export async function fetchNotificationSettings(): Promise<PlatformNotificationSettings> {
  return apiFetch<PlatformNotificationSettings>("/api/admin/v1/settings/notifications");
}

export async function fetchPlatformSmtp(): Promise<SmtpSettings> {
  return apiFetch<SmtpSettings>("/api/admin/v1/settings/smtp");
}

export async function savePlatformSmtp(body: SmtpSettings): Promise<SmtpSettings> {
  return apiFetch<SmtpSettings>("/api/admin/v1/settings/smtp", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function fetchEmailSettings(): Promise<EmailSettings> {
  return apiFetch<EmailSettings>("/api/admin/v1/settings/email");
}

export async function saveNotificationSettings(
  body: PlatformNotificationSettings & { adminRecipients?: string[] },
): Promise<void> {
  await apiFetch("/api/admin/v1/settings/notifications", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function saveEmailSettings(body: EmailSettings): Promise<void> {
  await apiFetch("/api/admin/v1/settings/email", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}


/** Platform-only console features (applications cannot override). */
export type PlatformFeatures = { configAsCode: boolean; connectApp: boolean; clientSetupCode: boolean };

export async function fetchPlatformFeatures(): Promise<PlatformFeatures> {
  return apiFetch<PlatformFeatures>("/api/admin/v1/settings/platform-features");
}

export async function savePlatformFeatures(changes: Partial<PlatformFeatures>): Promise<PlatformFeatures> {
  return apiFetch<PlatformFeatures>("/api/admin/v1/settings/platform-features", {
    method: "PUT",
    body: JSON.stringify(changes),
  });
}
