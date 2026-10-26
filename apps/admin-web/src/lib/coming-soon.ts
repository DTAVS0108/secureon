import type { AuthMethod } from "@/lib/types";

/**
 * Features the console shows but the auth-server does not run yet. They carry a "Coming soon"
 * badge and cannot be switched on (switching an already-on one off stays allowed).
 */

/** Feature flags with no working feature behind them (none today). */
export const COMING_SOON_FEATURE_FLAGS: ReadonlySet<string> = new Set<string>();

/** Auth methods are pending when the catalog says `implemented: false` (today: Push Notification). */
export function isAuthMethodComingSoon(method: AuthMethod): boolean {
  return method.implemented === false;
}

export function isFeatureFlagComingSoon(key: string): boolean {
  return COMING_SOON_FEATURE_FLAGS.has(key);
}
