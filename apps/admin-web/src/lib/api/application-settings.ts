import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import type {
  EmailSettings,
  EmailTemplatesMap,
  NotificationSettings,
  SmtpSettings,
  TestEmailRequest,
} from "@/lib/api/settings";
import type { AuthMethod, FeatureFlag, PasswordPolicy } from "@/lib/types";
import type { TokenPolicy } from "./token-policy";
import type { AppSettingsExposure } from "./app-exposure";

const base = (appId: string) => `/api/admin/v1/applications/${appId}/settings`;

function scoped(appId: string, init?: RequestInit): RequestInit {
  return { ...init, headers: { ...appScopeHeaders(appId), ...(init?.headers as Record<string, string>) } };
}

export type ApplicationSettingsWorkspace = {
  exposure: AppSettingsExposure;
  notifications?: NotificationSettings;
  email?: EmailSettings;
  authMethods?: AuthMethod[];
  passwordPolicy?: PasswordPolicy;
  featureFlags?: FeatureFlag[];
  appearance?: Record<string, unknown>;
};

export async function fetchApplicationSettingsWorkspace(
  appId: string,
): Promise<ApplicationSettingsWorkspace> {
  return apiFetch<ApplicationSettingsWorkspace>(`${base(appId)}/workspace`, scoped(appId));
}

export async function fetchApplicationNotifications(appId: string): Promise<NotificationSettings> {
  return apiFetch<NotificationSettings>(`${base(appId)}/notifications`, scoped(appId));
}

export async function saveApplicationNotifications(
  appId: string,
  body: NotificationSettings,
): Promise<NotificationSettings> {
  return apiFetch<NotificationSettings>(
    `${base(appId)}/notifications`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationNotifications(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/notifications`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationEmail(appId: string): Promise<EmailSettings> {
  return apiFetch<EmailSettings>(`${base(appId)}/email`, scoped(appId));
}

export async function saveApplicationEmail(appId: string, body: EmailSettings): Promise<EmailSettings> {
  return apiFetch<EmailSettings>(
    `${base(appId)}/email`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationEmail(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/email`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationSmtp(appId: string): Promise<SmtpSettings> {
  return apiFetch<SmtpSettings>(`${base(appId)}/smtp`, scoped(appId));
}

export async function saveApplicationSmtp(appId: string, body: SmtpSettings): Promise<SmtpSettings> {
  return apiFetch<SmtpSettings>(
    `${base(appId)}/smtp`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function fetchApplicationEmailTemplates(appId: string): Promise<EmailTemplatesMap> {
  return apiFetch<EmailTemplatesMap>(`${base(appId)}/email-templates`, scoped(appId));
}

export async function saveApplicationEmailTemplates(
  appId: string,
  body: EmailTemplatesMap,
): Promise<EmailTemplatesMap> {
  return apiFetch<EmailTemplatesMap>(
    `${base(appId)}/email-templates`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function fetchApplicationEmailTemplateDefaults(appId: string): Promise<EmailTemplatesMap> {
  return apiFetch<EmailTemplatesMap>(`${base(appId)}/email-templates/defaults`, scoped(appId));
}

export async function resetApplicationEmailTemplate(
  appId: string,
  templateKey: string,
): Promise<EmailTemplatesMap> {
  return apiFetch<EmailTemplatesMap>(
    `${base(appId)}/email-templates/${encodeURIComponent(templateKey)}`,
    scoped(appId, { method: "DELETE" }),
  );
}

export async function sendApplicationTestEmail(appId: string, body: TestEmailRequest): Promise<void> {
  await apiFetch(
    `${base(appId)}/email/test`,
    scoped(appId, { method: "POST", body: JSON.stringify(body) }),
  );
}

export async function fetchApplicationAuthMethods(appId: string): Promise<AuthMethod[]> {
  return apiFetch<AuthMethod[]>(`${base(appId)}/auth-methods`, scoped(appId));
}

export async function saveApplicationAuthMethods(appId: string, body: AuthMethod[]): Promise<AuthMethod[]> {
  return apiFetch<AuthMethod[]>(
    `${base(appId)}/auth-methods`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationAuthMethods(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/auth-methods`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationAppearance(appId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(`${base(appId)}/appearance`, scoped(appId));
}

export async function saveApplicationAppearance(
  appId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    `${base(appId)}/appearance`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationAppearance(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/appearance`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationPasswordPolicy(appId: string): Promise<PasswordPolicy> {
  return apiFetch<PasswordPolicy>(`${base(appId)}/password-policy`, scoped(appId));
}

export async function saveApplicationPasswordPolicy(
  appId: string,
  body: PasswordPolicy,
): Promise<PasswordPolicy> {
  return apiFetch<PasswordPolicy>(
    `${base(appId)}/password-policy`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationPasswordPolicy(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/password-policy`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationVerificationPolicy(
  appId: string,
): Promise<import("@/lib/api/verification-settings").VerificationPolicy> {
  return apiFetch(`${base(appId)}/verification-policy`, scoped(appId));
}

export async function saveApplicationVerificationPolicy(
  appId: string,
  body: import("@/lib/api/verification-settings").VerificationPolicy,
): Promise<import("@/lib/api/verification-settings").VerificationPolicy> {
  return apiFetch(`${base(appId)}/verification-policy`, scoped(appId, { method: "PUT", body: JSON.stringify(body) }));
}

export async function resetApplicationVerificationPolicy(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/verification-policy`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationMfaPolicy(
  appId: string,
): Promise<import("@/lib/api/mfa-policy").MfaPolicy> {
  return apiFetch(`${base(appId)}/mfa-policy`, scoped(appId));
}

export async function saveApplicationMfaPolicy(
  appId: string,
  body: import("@/lib/api/mfa-policy").MfaPolicy,
): Promise<import("@/lib/api/mfa-policy").MfaPolicy> {
  return apiFetch(`${base(appId)}/mfa-policy`, scoped(appId, { method: "PUT", body: JSON.stringify(body) }));
}

export async function resetApplicationMfaPolicy(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/mfa-policy`, scoped(appId, { method: "DELETE" }));
}

export async function fetchApplicationFeatureFlags(appId: string): Promise<FeatureFlag[]> {
  return apiFetch<FeatureFlag[]>(`${base(appId)}/feature-flags`, scoped(appId));
}

export async function saveApplicationFeatureFlags(
  appId: string,
  body: FeatureFlag[],
): Promise<FeatureFlag[]> {
  return apiFetch<FeatureFlag[]>(
    `${base(appId)}/feature-flags`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationFeatureFlags(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/feature-flags`, scoped(appId, { method: "DELETE" }));
}

export type ApplicationTokenTabState = {
  platformExposed: boolean;
  tabEnabled: boolean;
};

export type ApplicationMfaTabState = {
  platformExposed: boolean;
  tabEnabled: boolean;
};

export async function fetchApplicationTokenTabState(appId: string): Promise<ApplicationTokenTabState> {
  return apiFetch<ApplicationTokenTabState>(`${base(appId)}/token-policy/tab-state`, scoped(appId));
}

export async function setApplicationTokenTabEnabled(
  appId: string,
  enabled: boolean,
): Promise<ApplicationTokenTabState> {
  return apiFetch<ApplicationTokenTabState>(
    `${base(appId)}/token-policy/tab-enabled`,
    scoped(appId, { method: "PUT", body: JSON.stringify({ enabled }) }),
  );
}

export async function fetchApplicationMfaTabState(appId: string): Promise<ApplicationMfaTabState> {
  return apiFetch<ApplicationMfaTabState>(`${base(appId)}/mfa-tab/tab-state`, scoped(appId));
}

export async function setApplicationMfaTabEnabled(
  appId: string,
  enabled: boolean,
): Promise<ApplicationMfaTabState> {
  return apiFetch<ApplicationMfaTabState>(
    `${base(appId)}/mfa-tab/tab-enabled`,
    scoped(appId, { method: "PUT", body: JSON.stringify({ enabled }) }),
  );
}

export async function fetchApplicationTokenPolicy(appId: string): Promise<TokenPolicy> {
  return apiFetch<TokenPolicy>(`${base(appId)}/token-policy`, scoped(appId));
}

export async function saveApplicationTokenPolicy(appId: string, body: TokenPolicy): Promise<TokenPolicy> {
  return apiFetch<TokenPolicy>(
    `${base(appId)}/token-policy`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetApplicationTokenPolicy(appId: string): Promise<void> {
  await apiFetch<void>(`${base(appId)}/token-policy`, scoped(appId, { method: "DELETE" }));
}

export type IntegrationChecklist = {
  redirectUris: boolean;
  publicManifest: boolean;
  smtpConfigured: boolean;
  smsConfigured: boolean;
  authMethodsReviewed: boolean;
  signupReviewed: boolean;
  passwordResetTested: boolean;
  recoveryOtpTested: boolean;
  loginFlowTested: boolean;
  oauthPkceTested: boolean;
  mfaStepUpTested: boolean;
  sessionRevokeTested: boolean;
  magicLinkTested: boolean;
  federationTested: boolean;
  passkeyTested: boolean;
};

export type ClientIntegrationUrls = {
  forgotPassword?: string;
  passwordReset?: string;
  magicLink?: string;
};

export type ClientIntegrationConfig = {
  authUiMode: "hosted" | "native";
  clientUrls?: ClientIntegrationUrls;
  checklist: IntegrationChecklist;
  notes: string;
};

export async function fetchClientIntegration(appId: string): Promise<ClientIntegrationConfig> {
  return apiFetch<ClientIntegrationConfig>(`${base(appId)}/client-integration`, scoped(appId));
}

export async function saveClientIntegration(
  appId: string,
  body: ClientIntegrationConfig,
): Promise<ClientIntegrationConfig> {
  return apiFetch<ClientIntegrationConfig>(
    `${base(appId)}/client-integration`,
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export { listUsers as listApplicationUsers } from "./users";

export async function grantApplicationUser(appId: string, userId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/applications/${appId}/users/${userId}`, { method: "POST" });
}

export async function revokeApplicationUser(appId: string, userId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/applications/${appId}/users/${userId}`, { method: "DELETE" });
}
