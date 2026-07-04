import { browserApiFetch as apiFetch } from "./browser-client";
import { appScopeHeaders } from "./http";

export type VerificationPolicySection = {
  defaultMode?: string;
  allowedModes?: string[];
  requiredChannels?: string[];
  optionalChannels?: string[];
  allowUserChannelChoice?: boolean;
  otpLength?: number;
  otpTtlSeconds?: number;
  maxAttempts?: number;
  resendCooldownSeconds?: number;
  identifierTypes?: string[];
  passwordlessModes?: string[];
  mfaRequired?: string;
  passwordResetMode?: string;
  allowedChannels?: string[];
  requireVerifiedChannel?: boolean;
  fallbackToLink?: boolean;
};

export type VerificationPolicy = {
  signup?: VerificationPolicySection;
  login?: VerificationPolicySection;
  recovery?: VerificationPolicySection;
  inheritsPlatformDefaults?: boolean;
};

export type SmsTwilioSettings = {
  accountSid?: string;
  fromNumber?: string;
  authToken?: string;
  authTokenConfigured?: boolean;
};

export type SmsAwsSettings = {
  region?: string;
  accessKeyId?: string;
  secretAccessKey?: string;
  senderId?: string;
  secretAccessKeyConfigured?: boolean;
};

export type SmsMsg91Settings = {
  senderId?: string;
  templateId?: string;
  apiKey?: string;
  apiKeyConfigured?: boolean;
};

export type SmsSettings = {
  provider?: string;
  enabled?: boolean;
  configured?: boolean;
  smsConfigured?: boolean;
  inheritsPlatformDefaults?: boolean;
  scope?: string;
  availableProviders?: string[];
  twilio?: SmsTwilioSettings;
  aws_sns?: SmsAwsSettings;
  msg91?: SmsMsg91Settings;
  twilioFromNumber?: string;
};

export async function fetchVerificationPolicy(applicationId?: string): Promise<VerificationPolicy> {
  if (applicationId) {
    const { fetchApplicationVerificationPolicy } = await import("@/lib/api/application-settings");
    return fetchApplicationVerificationPolicy(applicationId);
  }
  return apiFetch<VerificationPolicy>("/api/admin/v1/settings/verification-policy");
}

export async function saveVerificationPolicy(
  policy: VerificationPolicy,
  applicationId?: string,
): Promise<VerificationPolicy> {
  if (applicationId) {
    const { saveApplicationVerificationPolicy } = await import("@/lib/api/application-settings");
    return saveApplicationVerificationPolicy(applicationId, policy);
  }
  return apiFetch<VerificationPolicy>("/api/admin/v1/settings/verification-policy", {
    method: "PUT",
    body: JSON.stringify(policy),
  });
}

export async function fetchSmsSettings(applicationId?: string): Promise<SmsSettings> {
  if (applicationId) {
    return fetchApplicationSms(applicationId);
  }
  return apiFetch<SmsSettings>("/api/admin/v1/settings/sms");
}

export async function saveSmsSettings(settings: SmsSettings): Promise<SmsSettings> {
  return apiFetch<SmsSettings>("/api/admin/v1/settings/sms", {
    method: "PUT",
    body: JSON.stringify(settings),
  });
}

export async function sendPlatformTestSms(to: string): Promise<void> {
  await apiFetch("/api/admin/v1/settings/sms/test", {
    method: "POST",
    body: JSON.stringify({ to }),
  });
}

function appSmsBase(appId: string) {
  return `/api/admin/v1/applications/${appId}/settings/sms`;
}

function scoped(appId: string, init?: RequestInit): RequestInit {
  return { ...init, headers: { ...appScopeHeaders(appId), ...(init?.headers as Record<string, string>) } };
}

export async function fetchApplicationSms(appId: string): Promise<SmsSettings> {
  return apiFetch<SmsSettings>(appSmsBase(appId), scoped(appId));
}

export async function saveApplicationSms(appId: string, settings: SmsSettings): Promise<SmsSettings> {
  return apiFetch<SmsSettings>(appSmsBase(appId), scoped(appId, { method: "PUT", body: JSON.stringify(settings) }));
}

export async function sendApplicationTestSms(appId: string, to: string): Promise<void> {
  await apiFetch(`${appSmsBase(appId)}/test`, scoped(appId, { method: "POST", body: JSON.stringify({ to }) }));
}
