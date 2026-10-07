"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import {
  fetchAuthMethods,
  fetchFeatureFlags,
  fetchPasswordPolicy,
  saveAuthMethods,
  saveFeatureFlags,
  savePasswordPolicy,
} from "@/lib/api/auth-settings";
import { ApiError } from "@/lib/api/http";
import {
  featureFlagsPayload,
  normalizeFeatureFlags,
  normalizePasswordPolicy,
  passwordPolicyPayload,
} from "@/lib/auth-settings-normalize";
import type { AuthMethod, FeatureFlag, PasswordPolicy } from "@/lib/types";

function formatError(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return "Request failed";
}

export async function loadAuthSettingsAction(applicationId?: string): Promise<{
  authMethods: AuthMethod[];
  featureFlags: FeatureFlag[];
  passwordPolicy: PasswordPolicy;
  error?: string;
}> {
  try {
    if (applicationId) {
      const {
        fetchApplicationAuthMethods,
        fetchApplicationFeatureFlags,
        fetchApplicationPasswordPolicy,
      } = await import("@/lib/api/application-settings");
      const [authMethods, featureFlagsRaw, passwordPolicyRaw] = await Promise.all([
        fetchApplicationAuthMethods(applicationId),
        fetchApplicationFeatureFlags(applicationId),
        fetchApplicationPasswordPolicy(applicationId),
      ]);
      return {
        authMethods,
        featureFlags: normalizeFeatureFlags(featureFlagsRaw),
        passwordPolicy: normalizePasswordPolicy(passwordPolicyRaw),
      };
    }
    const [authMethods, featureFlags, passwordPolicy] = await Promise.all([
      fetchAuthMethods(),
      fetchFeatureFlags(),
      fetchPasswordPolicy(),
    ]);
    return {
      authMethods,
      featureFlags: normalizeFeatureFlags(featureFlags),
      passwordPolicy: normalizePasswordPolicy(passwordPolicy),
    };
  } catch (e) {
    return {
      authMethods: [],
      featureFlags: [],
      passwordPolicy: normalizePasswordPolicy({}),
      error: formatError(e),
    };
  }
}

export async function saveAuthMethodsAction(
  methods: AuthMethod[],
  applicationId?: string,
): Promise<{ ok: boolean; error?: string }> {
  try {
    if (applicationId) {
      const { saveApplicationAuthMethods } = await import("@/lib/api/application-settings");
      await saveApplicationAuthMethods(applicationId, methods);
      return { ok: true };
    }
    await saveAuthMethods(methods);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatError(e) };
  }
}

export async function savePasswordPolicyAction(
  policy: PasswordPolicy,
  applicationId?: string,
): Promise<{ ok: boolean; passwordPolicy?: PasswordPolicy; error?: string }> {
  try {
    const payload = passwordPolicyPayload(policy);
    if (applicationId) {
      const { saveApplicationPasswordPolicy } = await import("@/lib/api/application-settings");
      const saved = await saveApplicationPasswordPolicy(applicationId, payload);
      return { ok: true, passwordPolicy: normalizePasswordPolicy(saved) };
    }
    const saved = await savePasswordPolicy(payload);
    return { ok: true, passwordPolicy: normalizePasswordPolicy(saved) };
  } catch (e) {
    return { ok: false, error: formatError(e) };
  }
}

export async function saveFeatureFlagsAction(
  flags: FeatureFlag[],
  applicationId?: string,
): Promise<{ ok: boolean; featureFlags?: FeatureFlag[]; error?: string }> {
  try {
    const payload = featureFlagsPayload(flags);
    if (applicationId) {
      const { saveApplicationFeatureFlags } = await import("@/lib/api/application-settings");
      const saved = await saveApplicationFeatureFlags(applicationId, payload);
      return { ok: true, featureFlags: normalizeFeatureFlags(saved) };
    }
    const saved = await saveFeatureFlags(payload);
    return { ok: true, featureFlags: normalizeFeatureFlags(saved) };
  } catch (e) {
    return { ok: false, error: formatError(e) };
  }
}

export async function resetApplicationAuthSettingsAction(
  applicationId: string,
  section: "auth-methods" | "password-policy" | "feature-flags",
): Promise<{ ok: boolean; error?: string }> {
  try {
    const api = await import("@/lib/api/application-settings");
    if (section === "auth-methods") await api.resetApplicationAuthMethods(applicationId);
    else if (section === "password-policy") await api.resetApplicationPasswordPolicy(applicationId);
    else await api.resetApplicationFeatureFlags(applicationId);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: formatError(e) };
  }
}
