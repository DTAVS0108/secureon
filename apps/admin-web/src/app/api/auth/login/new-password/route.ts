import { NextResponse } from "next/server";
import { authServerInternalUrl } from "@/lib/config";
import { forwardedHeadersFrom } from "@/lib/api/server-auth";
import { readJson } from "@/lib/auth/session-cookie";

/**
 * Replaces a temporary password with the one-time token from the sign-in answer. No session yet: the
 * operator then signs in with the new password (and the second step, when required).
 */
export async function POST(request: Request) {
  const body = (await request.json()) as { changeToken?: string; password?: string };
  if (!body.changeToken || !body.password) {
    return NextResponse.json({ detail: "Enter a new password." }, { status: 400 });
  }
  const res = await fetch(`${authServerInternalUrl()}/api/v1/account/password/reset`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...forwardedHeadersFrom(request.headers),
    },
    body: JSON.stringify({ token: body.changeToken, password: body.password }),
    cache: "no-store",
  });
  if (!res.ok) {
    const parsed = await readJson<{ detail?: string; message?: string }>(res);
    return NextResponse.json(
      { detail: parsed.detail ?? parsed.message ?? "Could not set the new password." },
      { status: res.status },
    );
  }
  return new NextResponse(null, { status: 204 });
}
