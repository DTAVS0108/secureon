import {
  fetchApplicationPolicySources,
  setApplicationPolicySource,
  type SettingsExposureKey,
} from "@/lib/api/application-policy";

/** Fired when an application section switches between the platform's settings and its own. */
export const POLICY_SOURCE_EVENT = "secureone:policy-source";

export function announcePolicySource(applicationId: string, exposureKey: SettingsExposureKey): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(POLICY_SOURCE_EVENT, { detail: { applicationId, exposureKey } }));
  }
}

/**
 * Call just before saving an application section: its first change gives the application its own copy (opening a
 * page never does, so a section nobody changed keeps following the platform).
 */
export async function ensureApplicationPolicyScope(
  applicationId: string,
  exposureKey: SettingsExposureKey,
): Promise<void> {
  const sources = await fetchApplicationPolicySources(applicationId);
  if (sources[exposureKey] !== "application") {
    await setApplicationPolicySource(applicationId, exposureKey, "application");
    announcePolicySource(applicationId, exposureKey);
  }
}

export async function ensureApplicationPolicyScopes(
  applicationId: string,
  exposureKeys: SettingsExposureKey[],
): Promise<void> {
  await Promise.all(exposureKeys.map((key) => ensureApplicationPolicyScope(applicationId, key)));
}
