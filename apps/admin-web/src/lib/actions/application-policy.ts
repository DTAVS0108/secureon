"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import {
  fetchApplicationPolicySource,
  fetchApplicationPolicySources,
  setApplicationPolicySource,
  type PolicySource,
  type SettingsExposureKey,
} from "@/lib/api/application-policy";
import { ApiError } from "@/lib/api/http";

function formatError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 404) {
      return "Policy source API not found — restart apps/auth-server (./gradlew bootRun) and reload this page.";
    }
    return e.message;
  }
  if (e instanceof Error) return e.message;
  return "Request failed";
}

export async function loadApplicationPolicySourcesAction(applicationId: string): Promise<{
  sources: Partial<Record<SettingsExposureKey, PolicySource>>;
  error?: string;
}> {
  try {
    return { sources: await fetchApplicationPolicySources(applicationId) };
  } catch (e) {
    return { sources: {}, error: formatError(e) };
  }
}

export async function loadApplicationPolicySourceAction(
  applicationId: string,
  exposureKey: SettingsExposureKey,
): Promise<{ policySource: PolicySource; error?: string }> {
  try {
    return { policySource: await fetchApplicationPolicySource(applicationId, exposureKey) };
  } catch (e) {
    return { policySource: "platform", error: formatError(e) };
  }
}

export async function setApplicationPolicySourceAction(
  applicationId: string,
  exposureKey: SettingsExposureKey,
  source: PolicySource,
): Promise<{ policySource: PolicySource; error?: string }> {
  try {
    const policySource = await setApplicationPolicySource(applicationId, exposureKey, source);
    return { policySource };
  } catch (e) {
    return { policySource: source, error: formatError(e) };
  }
}
