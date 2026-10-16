import "server-only";

import { headers } from "next/headers";
import { buildAdminAuthHeader } from "@/lib/auth/session-shared";
import { getAdminSession } from "@/lib/auth/session-server";
import { SERVER_AUTH_HOOK, SERVER_FORWARD_HOOK } from "./http";

/** Auth header for the signed-in console user, or null when there is no valid session cookie. */
export async function adminAuthHeader(): Promise<string | null> {
  const session = await getAdminSession();
  return session ? buildAdminAuthHeader(session) : null;
}

/**
 * In the single image the console listens on 127.0.0.1 only and every request reaches it through
 * the auth-server's /admin proxy, which sets X-Forwarded-For to the real client address. Passing it
 * on lets the auth-server rate-limit and audit console sign-ins per client instead of per console.
 * Off unless SECUREONE_TRUST_PROXY_HEADERS=true: a console reachable directly must not let callers
 * pick their own address (the auth-server trusts the header from private addresses).
 */
const TRUST_PROXY_HEADERS = process.env.SECUREONE_TRUST_PROXY_HEADERS === "true";

export function forwardedHeadersFrom(source: Headers): Record<string, string> {
  if (!TRUST_PROXY_HEADERS) return {};
  const forwardedFor = source.get("x-forwarded-for");
  return forwardedFor ? { "X-Forwarded-For": forwardedFor } : {};
}

/** Forwarded headers of the request currently being rendered or handled (server components, actions). */
export async function requestForwardedHeaders(): Promise<Record<string, string>> {
  if (!TRUST_PROXY_HEADERS) return {};
  try {
    return forwardedHeadersFrom(await headers());
  } catch {
    // Outside a request (build, background work): nothing to forward.
    return {};
  }
}

// Shared API helpers (browser-client) run during server rendering too; they look these up so they
// act as the signed-in user instead of a fixed account. Imported by the root layout.
(globalThis as Record<string, unknown>)[SERVER_AUTH_HOOK] = adminAuthHeader;
(globalThis as Record<string, unknown>)[SERVER_FORWARD_HOOK] = requestForwardedHeaders;
