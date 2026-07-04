import type { AppSettingsExposure } from "@/lib/api/app-exposure";

/** Must match SettingsExposureService.SECTION_KEYS on auth-server. */
export const APP_SETTINGS_EXPOSURE_KEYS = [
  "notifications",
  "email",
  "auth-methods",
  "password-policy",
  "feature-flags",
  "appearance",
  "user-directory",
  "public-manifest",
  "token-policy",
  "verification-policy",
  "sms",
] as const;

/** Used when auth-server has no app-exposure API yet (restart required for V10+). */
export const DEFAULT_APP_SETTINGS_EXPOSURE: AppSettingsExposure = {
  notifications: true,
  email: true,
  "auth-methods": true,
  "password-policy": true,
  "feature-flags": true,
  appearance: false,
  "user-directory": true,
  "public-manifest": true,
  "token-policy": true,
  "verification-policy": true,
  sms: true,
};

export function resolveAppSettingsExposure(
  raw: AppSettingsExposure | null | undefined,
): AppSettingsExposure {
  if (!raw || Object.keys(raw).length === 0) {
    return { ...DEFAULT_APP_SETTINGS_EXPOSURE };
  }
  return { ...DEFAULT_APP_SETTINGS_EXPOSURE, ...raw };
}

/** Matches SettingsExposureService.defaultExposure on auth-server. */
function defaultExposureForKey(key: string): boolean {
  return key !== "appearance";
}

/**
 * Resolves platform exposure for application settings tabs.
 * Uses explicit API values when present; otherwise mirrors server defaults for missing keys.
 */
export function coerceAppSettingsExposure(
  raw: AppSettingsExposure | null | undefined,
): AppSettingsExposure {
  const merged = raw ?? {};
  const result: AppSettingsExposure = {};
  for (const key of APP_SETTINGS_EXPOSURE_KEYS) {
    if (key in merged && merged[key] !== undefined) {
      result[key] = Boolean(merged[key]);
    } else {
      result[key] = defaultExposureForKey(key);
    }
  }
  return result;
}

/** On API failure: hide all optional tabs (no optimistic defaults). */
export function strictAppSettingsExposure(
  raw: AppSettingsExposure | null | undefined,
): AppSettingsExposure {
  return normalizeAppSettingsExposure(raw);
}

/** Ensures every exposure key is present before save (avoids partial PUT wiping newer keys). */
export function normalizeAppSettingsExposure(
  raw: AppSettingsExposure | null | undefined,
): AppSettingsExposure {
  const merged = raw ?? {};
  const result: AppSettingsExposure = {};
  for (const key of APP_SETTINGS_EXPOSURE_KEYS) {
    result[key] = Boolean(merged[key]);
  }
  return result;
}
