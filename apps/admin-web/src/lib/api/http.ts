const ACT_AS_EMAIL = process.env.SECUREONE_ACT_AS_EMAIL;

/** Headers for application-scoped admin API calls. */
export function appScopeHeaders(applicationId: string): Record<string, string> {
  const headers: Record<string, string> = { "X-Application-Id": applicationId };
  if (ACT_AS_EMAIL) headers["X-Act-As-Email"] = ACT_AS_EMAIL;
  return headers;
}

/** globalThis key under which server-auth.ts registers the server-side auth header lookup. */
export const SERVER_AUTH_HOOK = "__secureoneAdminAuthHeader";

/** Server-side auth header for the signed-in user; null when signed out or the hook is not loaded. */
export async function serverAuthHeader(): Promise<string | null> {
  const hook = (globalThis as Record<string, unknown>)[SERVER_AUTH_HOOK];
  return typeof hook === "function" ? ((await hook()) as string | null) : null;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
