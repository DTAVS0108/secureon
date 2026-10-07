export const SESSION_COOKIE = "secureone_admin_session";
/** Opaque console session token issued by POST /api/admin/v1/auth/login. */
export const ADMIN_SESSION_SCHEME = "SecureOne-Admin-Session";

/** 32 random bytes, base64url without padding. */
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{43}$/;

export type AdminSession = {
  token: string;
};

export function encodeSession(session: AdminSession): string {
  return session.token;
}

export function decodeSession(value: string): AdminSession | null {
  return TOKEN_FORMAT.test(value) ? { token: value } : null;
}

/** Auth header for admin API calls. The password never leaves the login request. */
export function buildAdminAuthHeader(session: AdminSession): string {
  return `${ADMIN_SESSION_SCHEME} ${session.token}`;
}

/** Build login username: platform admin or tenantSlug:email */
export function buildLoginUsername(tenantSlug: string | undefined, emailOrUsername: string): string {
  const value = emailOrUsername.trim();
  if (!tenantSlug?.trim()) {
    return value;
  }
  return `${tenantSlug.trim().toLowerCase()}:${value.toLowerCase()}`;
}
