import { NextResponse } from "next/server";
import { authServerInternalUrl } from "@/lib/config";
import { forwardedHeadersFrom } from "@/lib/api/server-auth";
import { type IssuedSession, readJson, sessionResponse } from "@/lib/auth/session-cookie";

/** Second sign-in step: an authenticator or recovery code, or confirming a new authenticator. */
export async function POST(request: Request) {
  const body = (await request.json()) as {
    action?: "verify" | "enroll" | "passkey-options" | "passkey-verify";
    mfaToken?: string;
    code?: string;
    recoveryCode?: string;
    factorId?: string;
    requestId?: string;
    credential?: string;
  };
  if (!body.mfaToken) {
    return NextResponse.json({ detail: "Sign-in expired. Enter your password again." }, { status: 401 });
  }
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
    ...forwardedHeadersFrom(request.headers),
  };
  if (body.action === "passkey-options") {
    // Assertion options only: no session yet, so the answer goes back to the browser unchanged.
    const res = await fetch(`${authServerInternalUrl()}/api/admin/v1/auth/mfa/passkey/options`, {
      method: "POST",
      headers,
      body: JSON.stringify({ mfaToken: body.mfaToken }),
      cache: "no-store",
    });
    return NextResponse.json(await readJson<Record<string, unknown>>(res), { status: res.status });
  }
  if (body.action === "passkey-verify") {
    const res = await fetch(`${authServerInternalUrl()}/api/admin/v1/auth/mfa/passkey/verify`, {
      method: "POST",
      headers,
      body: JSON.stringify({ mfaToken: body.mfaToken, requestId: body.requestId, credential: body.credential }),
      cache: "no-store",
    });
    const parsed = await readJson<IssuedSession & { detail?: string }>(res);
    if (!res.ok) {
      return NextResponse.json({ detail: parsed.detail ?? "The passkey was not accepted." }, { status: res.status });
    }
    return sessionResponse(request, parsed);
  }
  const enrolling = body.action === "enroll";
  const res = await fetch(
    `${authServerInternalUrl()}/api/admin/v1/auth/mfa/${enrolling ? "enroll/confirm" : "verify"}`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...forwardedHeadersFrom(request.headers),
      },
      body: JSON.stringify(
        enrolling
          ? { mfaToken: body.mfaToken, factorId: body.factorId, code: body.code?.trim() }
          : {
              mfaToken: body.mfaToken,
              code: body.code?.trim() || undefined,
              recoveryCode: body.recoveryCode?.trim() || undefined,
            },
      ),
      cache: "no-store",
    },
  );
  const parsed = await readJson<IssuedSession & { detail?: string }>(res);
  if (!res.ok) {
    return NextResponse.json({ detail: parsed.detail ?? "That code is not valid." }, { status: res.status });
  }
  return sessionResponse(request, parsed, parsed.recoveryCodes ? { recoveryCodes: parsed.recoveryCodes } : {});
}
