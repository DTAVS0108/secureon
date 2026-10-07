"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import {
  fetchAppExposure,
  fetchApplicationExposure,
  saveAppExposure,
  type AppSettingsExposure,
} from "@/lib/api/app-exposure";
import { ApiError } from "@/lib/api/http";
import {
  coerceAppSettingsExposure,
  DEFAULT_APP_SETTINGS_EXPOSURE,
  normalizeAppSettingsExposure,
  strictAppSettingsExposure,
} from "@/lib/settings-exposure";

function formatError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 404) {
      return "Auth server is missing settings API — restart apps/auth-server (./gradlew bootRun).";
    }
    return e.message;
  }
  if (e instanceof Error) return e.message;
  return "Request failed";
}

export async function loadAppExposureAction(): Promise<{
  exposure: AppSettingsExposure;
  error?: string;
}> {
  try {
    return { exposure: coerceAppSettingsExposure(await fetchAppExposure()) };
  } catch (e) {
    return { exposure: { ...DEFAULT_APP_SETTINGS_EXPOSURE }, error: formatError(e) };
  }
}

export async function saveAppExposureAction(
  body: AppSettingsExposure,
): Promise<{ exposure: AppSettingsExposure; error?: string }> {
  const payload = normalizeAppSettingsExposure(body);
  try {
    const saved = await saveAppExposure(payload);
    return { exposure: normalizeAppSettingsExposure(saved) };
  } catch (e) {
    return { exposure: payload, error: formatError(e) };
  }
}

export async function loadApplicationExposureAction(applicationId: string): Promise<{
  exposure: AppSettingsExposure;
  error?: string;
}> {
  try {
    return { exposure: coerceAppSettingsExposure(await fetchApplicationExposure(applicationId)) };
  } catch (e) {
    return { exposure: strictAppSettingsExposure(null), error: formatError(e) };
  }
}
