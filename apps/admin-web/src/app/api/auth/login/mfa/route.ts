import { NextResponse } from "next/server";
import { AUTH_SERVER_URL } from "@/lib/config";
import { type IssuedSession, readJson, sessionResponse } from "@/lib/auth/session-cookie";

/** Second sign-in step: an authenticator or recovery code, or confirming a new authenticator. */
export async function POST(request: Request) {
  const body = (await request.json()) as {
    action?: "verify" | "enroll";
    mfaToken?: string;
    code?: string;
    recoveryCode?: string;
    factorId?: string;
  };
  if (!body.mfaToken) {
    return NextResponse.json({ detail: "Sign-in expired. Enter your password again." }, { status: 401 });
  }
  const enrolling = body.action === "enroll";
  const res = await fetch(
    `${AUTH_SERVER_URL}/api/admin/v1/auth/mfa/${enrolling ? "enroll/confirm" : "verify"}`,
    {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
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
