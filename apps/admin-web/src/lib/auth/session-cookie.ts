import { NextResponse } from "next/server";
import { isProd } from "@/lib/config";
import { decodeSession, encodeSession, SESSION_COOKIE } from "@/lib/auth/session-shared";

/** Auth-server sign-in result once every step (password, then MFA) is done. */
export type IssuedSession = { sessionToken?: string; expiresIn?: number; recoveryCodes?: string[] };

/**
 * Stores the auth-server's opaque session token in an httpOnly cookie and returns `extra` as JSON.
 * The cookie lives as long as the session's absolute lifetime; the auth-server also ends it after
 * inactivity.
 */
export function sessionResponse(request: Request, issued: IssuedSession, extra: Record<string, unknown> = {}) {
  const session = issued.sessionToken ? decodeSession(issued.sessionToken) : null;
  if (!session) {
    return NextResponse.json(
      { detail: "Sign-in succeeded but the auth-server returned no session token. Update auth-server." },
      { status: 502 },
    );
  }
  const response = NextResponse.json({ ok: true, ...extra });
  response.cookies.set(SESSION_COOKIE, encodeSession(session), {
    httpOnly: true,
    sameSite: "lax",
    // Behind the /admin proxy the console itself is reached over http; the proxy reports the real scheme.
    secure:
      isProd() ||
      new URL(request.url).protocol === "https:" ||
      request.headers.get("x-forwarded-proto") === "https",
    path: "/",
    maxAge: issued.expiresIn ?? 60 * 60 * 8,
  });
  return response;
}

export async function readJson<T>(res: Response): Promise<T> {
  const text = await res.text();
  try {
    return JSON.parse(text) as T;
  } catch {
    return {} as T;
  }
}
