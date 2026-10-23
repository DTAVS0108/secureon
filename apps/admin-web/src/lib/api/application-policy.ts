import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";

export type PolicySource = "platform" | "application";

export type SettingsExposureKey =
  | "notifications"
  | "email"
  | "auth-methods"
  | "two-factor"
  | "password-policy"
  | "feature-flags"
  | "appearance"
  | "user-directory"
  | "public-manifest"
  | "token-policy"
  | "verification-policy"
  | "sms";

const base = (appId: string) => `/api/admin/v1/applications/${appId}/settings`;

function scoped(appId: string, init?: RequestInit): RequestInit {
  return { ...init, headers: { ...appScopeHeaders(appId), ...(init?.headers as Record<string, string>) } };
}

export async function fetchApplicationPolicySources(
  appId: string,
): Promise<Partial<Record<SettingsExposureKey, PolicySource>>> {
  return apiFetch<Partial<Record<SettingsExposureKey, PolicySource>>>(
    `${base(appId)}/policy-sources`,
    scoped(appId),
  );
}

export async function fetchApplicationPolicySource(
  appId: string,
  exposureKey: SettingsExposureKey,
): Promise<PolicySource> {
  const res = await apiFetch<{ policySource: PolicySource }>(
    `${base(appId)}/policy-source/${exposureKey}`,
    scoped(appId),
  );
  return res.policySource;
}

export async function setApplicationPolicySource(
  appId: string,
  exposureKey: SettingsExposureKey,
  source: PolicySource,
): Promise<PolicySource> {
  const res = await apiFetch<{ policySource: PolicySource }>(
    `${base(appId)}/policy-source/${exposureKey}`,
    scoped(appId, { method: "PUT", body: JSON.stringify({ source }) }),
  );
  return res.policySource;
}
