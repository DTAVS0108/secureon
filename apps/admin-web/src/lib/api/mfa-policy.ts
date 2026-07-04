import { browserApiFetch as apiFetch } from "./browser-client";

export type MfaPolicy = {
  stepUpAfterPassword?: boolean;
  requirePasskeyWhenEnrolled?: boolean;
  recoveryCodesPerEnrollment?: number;
  challengeTtlSeconds?: number;
  maxChallengeAttempts?: number;
  rememberDeviceDays?: number;
  scope?: string;
  inheritsPlatformDefaults?: boolean;
};

export async function fetchPlatformMfaPolicy(): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>("/api/admin/v1/settings/mfa-policy");
}

export async function savePlatformMfaPolicy(body: MfaPolicy): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>("/api/admin/v1/settings/mfa-policy", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
