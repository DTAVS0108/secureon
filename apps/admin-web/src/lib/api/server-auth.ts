import "server-only";

import { buildAdminAuthHeader } from "@/lib/auth/session-shared";
import { getAdminSession } from "@/lib/auth/session-server";
import { SERVER_AUTH_HOOK } from "./http";

/** Auth header for the signed-in console user, or null when there is no valid session cookie. */
export async function adminAuthHeader(): Promise<string | null> {
  const session = await getAdminSession();
  return session ? buildAdminAuthHeader(session) : null;
}

// Shared API helpers (browser-client) run during server rendering too; they look this up so they
// act as the signed-in user instead of a fixed account. Imported by the root layout.
(globalThis as Record<string, unknown>)[SERVER_AUTH_HOOK] = adminAuthHeader;
