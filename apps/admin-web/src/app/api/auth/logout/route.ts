import { NextResponse } from "next/server";
import { authServerInternalUrl } from "@/lib/config";
import { SESSION_COOKIE } from "@/lib/auth/session-shared";
import { adminAuthHeader, forwardedHeadersFrom } from "@/lib/api/server-auth";

export async function POST(request: Request) {
  const authorization = await adminAuthHeader();
  if (authorization) {
    // Revoke the token on the auth-server so a copied cookie stops working too.
    await fetch(`${authServerInternalUrl()}/api/admin/v1/auth/logout`, {
      method: "POST",
      headers: { Authorization: authorization, ...forwardedHeadersFrom(request.headers) },
      cache: "no-store",
    }).catch(() => undefined);
  }

  const response = NextResponse.json({ ok: true });
  response.cookies.set(SESSION_COOKIE, "", {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
  return response;
}
