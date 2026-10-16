import "server-only";

import { redirect } from "next/navigation";
import { authServerInternalUrl } from "@/lib/config";
import { ApiError } from "./http";
import { adminAuthHeader, requestForwardedHeaders } from "./server-auth";

const ACT_AS_EMAIL = process.env.SECUREONE_ACT_AS_EMAIL;

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const authorization = await adminAuthHeader();
  if (!authorization) {
    redirect("/login");
  }

  const url = `${authServerInternalUrl()}${path.startsWith("/") ? path : `/${path}`}`;
  const baseHeaders: Record<string, string> = {
    Authorization: authorization,
    Accept: "application/json",
    ...(await requestForwardedHeaders()),
  };
  if (ACT_AS_EMAIL) baseHeaders["X-Act-As-Email"] = ACT_AS_EMAIL;

  const res = await fetch(url, {
    ...init,
    cache: "no-store",
    headers: {
      ...baseHeaders,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(init?.headers as Record<string, string> | undefined),
    },
  });

  if (res.status === 401) {
    // Session expired or revoked on the auth-server.
    redirect("/login?reason=expired");
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
