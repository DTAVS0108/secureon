import { browserApiFetch as apiFetch } from "./browser-client";

export type TwoStepMode = "off" | "optional" | "required";
export type TwoStepStrength = "any" | "strong" | "phishing_resistant";
export type TwoStepMethodId = "passkey" | "security_key" | "totp" | "sms" | "email";
export type LockMode = "open" | "stricter" | "locked";

/** Two-step verification policy (setting `mfa_policy`). */
export type MfaPolicy = {
  mode?: TwoStepMode;
  minimumStrength?: TwoStepStrength;
  methods?: TwoStepMethodId[];
  defaultMethod?: TwoStepMethodId;
  promptOptional?: boolean;
  promptIntervalDays?: number;
  allowDontAskAgain?: boolean;
  graceDays?: number;
  graceSkips?: number;
  enforceFrom?: string;
  reminderDays?: number[];
  rememberDeviceDays?: number;
  challengeTtlSeconds?: number;
  maxChallengeAttempts?: number;
  recoveryCodesPerEnrollment?: number;
  freshStepMinutes?: number;
  stepUpAfterPassword?: boolean;
  /** Locks this level sets for the levels below (platform, tenant). */
  locks?: Partial<Record<string, LockMode>>;
  /** Locks the level above set (tenant view: the platform's). */
  platformLocks?: Partial<Record<string, LockMode>>;
  /** Keys this level sets itself (tenant, application). */
  overrides?: string[];
  /** What an application would get without its own values. */
  inherited?: MfaPolicy;
  scope?: string;
  inheritsPlatformDefaults?: boolean;
  /** Application: false while its "Two Factor Authentication" switch is off (its own values are not applied). */
  ownSettings?: boolean;
  /** Application: its switch is off, so two-factor is off for it (unless the platform or tenant locks it). */
  switchedOff?: boolean;
  /** A weaker change of these settings waiting for another admin's approval (Access review). */
  pendingChange?: import("./change-requests").ChangeRequest | null;
};

export const TWO_STEP_METHODS: { id: TwoStepMethodId; label: string; strength: TwoStepStrength; hint: string }[] = [
  { id: "passkey", label: "Passkey", strength: "phishing_resistant", hint: "Fingerprint, face or device PIN. Cannot be phished." },
  { id: "security_key", label: "Security key", strength: "phishing_resistant", hint: "USB or NFC key (YubiKey). Cannot be phished." },
  { id: "totp", label: "Authenticator app", strength: "strong", hint: "6-digit codes from an app." },
  { id: "sms", label: "Text message code", strength: "any", hint: "Restricted by NIST: SIM swaps can steal it. Needs an SMS provider." },
  { id: "email", label: "Email code", strength: "any", hint: "Weakest: NIST says email is not a real second factor." },
];

export async function fetchPlatformMfaPolicy(): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>("/api/admin/v1/settings/mfa-policy");
}

export async function savePlatformMfaPolicy(body: MfaPolicy): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>("/api/admin/v1/settings/mfa-policy", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function fetchTenantMfaPolicy(tenantId: string): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>(`/api/admin/v1/tenants/${tenantId}/two-step-policy`);
}

export async function saveTenantMfaPolicy(tenantId: string, body: MfaPolicy): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>(`/api/admin/v1/tenants/${tenantId}/two-step-policy`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function resetTenantMfaPolicy(tenantId: string): Promise<MfaPolicy> {
  return apiFetch<MfaPolicy>(`/api/admin/v1/tenants/${tenantId}/two-step-policy`, { method: "DELETE" });
}

// ---- A user's two-step verification (admins) -----------------------------------------------------------------

export type TwoStepUserStatus = "enabled" | "off" | "in_grace" | "overdue" | "not_set_up" | "exempt";

export type TwoStepUserMethod = {
  id: string;
  type: TwoStepMethodId;
  label: string;
  masked?: string | null;
  isDefault: boolean;
  strength: string;
  createdAt?: string | null;
  lastUsedAt?: string | null;
};

export type TwoStepUserView = {
  status: TwoStepUserStatus;
  mode: TwoStepMode;
  minimumStrength: TwoStepStrength;
  methods: TwoStepUserMethod[];
  allowedMethods: TwoStepMethodId[];
  backupCodesRemaining: number;
  deadline: string | null;
  skipsUsed: number;
  exemptUntil: string | null;
  exemptReason: string | null;
  exemptedBy: string | null;
  /** "application": this application only; "all": every application of the tenant. */
  exemptScope?: "application" | "all" | null;
  /** An exemption of this user waiting for another admin's approval (Access review). */
  pendingChange?: import("./change-requests").ChangeRequest | null;
  extendedBy: string | null;
  rememberedDevices: number;
  canExempt: boolean;
  maxExtendDays: number;
};

export function fetchUserTwoStep(userId: string, applicationId?: string): Promise<TwoStepUserView> {
  return apiFetch<TwoStepUserView>(
    applicationId
      ? `/api/admin/v1/applications/${applicationId}/users/${userId}/two-step`
      : `/api/admin/v1/users/${userId}/two-step`,
  );
}

export function resetUserTwoStep(userId: string, method: TwoStepMethodId | null, applicationId?: string) {
  return apiFetch<TwoStepUserView>(`/api/admin/v1/users/${userId}/two-step/reset`, {
    method: "POST",
    body: JSON.stringify({ method, applicationId: applicationId ?? null }),
  });
}

export function extendUserTwoStep(userId: string, days: number, applicationId?: string) {
  return apiFetch<TwoStepUserView>(`/api/admin/v1/users/${userId}/two-step/extend`, {
    method: "POST",
    body: JSON.stringify({ days, applicationId: applicationId ?? null }),
  });
}

/** {@code applicationId} null or omitted: every application of the tenant. */
export function exemptUserTwoStep(userId: string, until: string, reason: string, applicationId?: string | null) {
  return apiFetch<TwoStepUserView>(`/api/admin/v1/users/${userId}/two-step/exempt`, {
    method: "POST",
    body: JSON.stringify({ until, reason, applicationId: applicationId ?? null }),
  });
}

/** Ends the exemption in one application, or the one for every application ({@code applicationId} omitted). */
export function endUserTwoStepExemption(userId: string, applicationId?: string | null) {
  const query = applicationId ? `?applicationId=${encodeURIComponent(applicationId)}` : "";
  return apiFetch<TwoStepUserView>(`/api/admin/v1/users/${userId}/two-step/exempt${query}`, { method: "DELETE" });
}

export function fetchUserTwoStepStatuses(applicationId: string, userIds: string[]): Promise<Record<string, TwoStepUserStatus>> {
  return apiFetch<Record<string, TwoStepUserStatus>>(
    `/api/admin/v1/applications/${applicationId}/users/two-step-statuses`,
    { method: "POST", body: JSON.stringify({ userIds }) },
  );
}

export const TWO_STEP_STATUS_LABELS: Record<TwoStepUserStatus, string> = {
  enabled: "2FA on",
  off: "2FA off",
  in_grace: "2FA due",
  overdue: "2FA overdue",
  not_set_up: "2FA required",
  exempt: "2FA exempt",
};
