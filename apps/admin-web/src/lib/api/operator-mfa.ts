import { browserApiFetch as apiFetch } from "./browser-client";

export type MfaFactor = {
  id: string;
  type: string;
  label: string;
  verified: boolean;
  createdAt?: string;
  lastUsedAt?: string;
};

export async function listOperatorMfaFactors(): Promise<MfaFactor[]> {
  return apiFetch<MfaFactor[]>("/api/admin/v1/me/mfa/factors");
}

export async function enrollOperatorTotp(label?: string): Promise<{
  factorId: string;
  secret: string;
  otpauthUri: string;
  qrCodeDataUrl: string;
}> {
  return apiFetch("/api/admin/v1/me/mfa/totp/enroll", {
    method: "POST",
    body: JSON.stringify(label ? { label } : {}),
  });
}

export async function confirmOperatorTotp(
  factorId: string,
  code: string,
): Promise<{ factorId: string; recoveryCodes: string[] }> {
  return apiFetch("/api/admin/v1/me/mfa/totp/confirm", {
    method: "POST",
    body: JSON.stringify({ factorId, code }),
  });
}

export async function deleteOperatorTotp(factorId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/me/mfa/totp/${factorId}`, { method: "DELETE" });
}

export async function beginOperatorPasskeyRegistration(label?: string): Promise<{
  requestId: string;
  publicKey: string;
}> {
  return apiFetch("/api/admin/v1/me/mfa/passkey/register/options", {
    method: "POST",
    body: JSON.stringify(label ? { label } : {}),
  });
}

export async function finishOperatorPasskeyRegistration(
  requestId: string,
  credential: string,
): Promise<{ factorId: string }> {
  return apiFetch("/api/admin/v1/me/mfa/passkey/register/verify", {
    method: "POST",
    body: JSON.stringify({ requestId, credential }),
  });
}

export async function deleteOperatorPasskey(factorId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/me/mfa/passkey/${factorId}`, { method: "DELETE" });
}
