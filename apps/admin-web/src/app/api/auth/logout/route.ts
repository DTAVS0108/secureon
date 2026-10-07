import { NextResponse } from "next/server";
import { AUTH_SERVER_URL } from "@/lib/config";
import { SESSION_COOKIE } from "@/lib/auth/session-shared";
import { adminAuthHeader } from "@/lib/api/server-auth";

export async function POST() {
  const authorization = await adminAuthHeader();
  if (authorization) {
    // Revoke the token on the auth-server so a copied cookie stops working too.
    await fetch(`${AUTH_SERVER_URL}/api/admin/v1/auth/logout`, {
      method: "POST",
      headers: { Authorization: authorization },
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
