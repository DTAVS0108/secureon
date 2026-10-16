import { redirect } from "next/navigation";
import { authServerInternalUrl, stripBasePath, withBasePath } from "@/lib/config";
import { ApiError, serverAuthHeader, serverForwardedHeaders } from "./http";

const ACT_AS_EMAIL = process.env.SECUREONE_ACT_AS_EMAIL;

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

/**
 * Admin API fetch safe for client components.
 * Browser: proxies through /api/proxy with the session cookie.
 * Server rendering: calls the auth-server directly as the signed-in user (see server-auth.ts);
 * with no session it redirects to /login rather than falling back to any other account.
 */
export async function browserApiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const normalized = path.startsWith("/") ? path.slice(1) : path;

  const baseHeaders: Record<string, string> = {
    Accept: "application/json",
    ...(init?.body ? { "Content-Type": "application/json" } : {}),
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (ACT_AS_EMAIL) baseHeaders["X-Act-As-Email"] = ACT_AS_EMAIL;

  const url = isBrowser()
    ? withBasePath(`/api/proxy/${normalized}`)
    : `${authServerInternalUrl()}/${normalized}`;

  if (!isBrowser()) {
    const authorization = await serverAuthHeader();
    if (!authorization) {
      redirect("/login");
    }
    baseHeaders.Authorization = authorization;
    Object.assign(baseHeaders, await serverForwardedHeaders());
  }

  const res = await fetch(url, {
    ...init,
    cache: "no-store",
    credentials: isBrowser() ? "same-origin" : undefined,
    headers: baseHeaders,
  });

  if (res.status === 401) {
    // Session ended on the auth-server (signed out, idle timeout or lifetime reached).
    if (!isBrowser()) {
      redirect("/login?reason=expired");
    }
    const here = `${stripBasePath(window.location.pathname)}${window.location.search}`;
    window.location.assign(withBasePath(`/login?reason=expired&next=${encodeURIComponent(here)}`));
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }

  if (!res.ok) {
    const detail =
      typeof body === "object" && body !== null && "detail" in body
        ? String((body as { detail: unknown }).detail)
        : `HTTP ${res.status}`;
    throw new ApiError(detail, res.status, body);
  }

  return body as T;
}

/** @deprecated Use browserApiFetch */
export const apiFetch = browserApiFetch;
