import { NextResponse } from "next/server";
import { AUTH_SERVER_URL } from "@/lib/config";
import { readJson, sessionResponse } from "@/lib/auth/session-cookie";

type LoginResult = {
  detail?: string;
  sessionToken?: string;
  expiresIn?: number;
  mfaRequired?: boolean;
  mfaEnrollmentRequired?: boolean;
  mfaToken?: string;
  methods?: string[];
  factorId?: string;
  secret?: string;
  otpauthUri?: string;
  qrCodeDataUrl?: string;
};

export async function POST(request: Request) {
  const body = (await request.json()) as {
    emailOrUsername?: string;
    password?: string;
    tenantSlug?: string;
  };
  const emailOrUsername = body.emailOrUsername?.trim();
  const password = body.password ?? "";
  if (!emailOrUsername || !password) {
    return NextResponse.json({ detail: "Username and password are required." }, { status: 400 });
  }

  const res = await fetch(`${AUTH_SERVER_URL}/api/admin/v1/auth/login`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      tenantSlug: body.tenantSlug?.trim() || undefined,
      emailOrUsername,
      password,
    }),
    cache: "no-store",
  });

  const parsed = await readJson<LoginResult>(res);
  if (!res.ok) {
    return NextResponse.json({ detail: parsed.detail ?? "Invalid credentials." }, { status: res.status });
  }

  // Password accepted; the second step happens at /api/auth/login/mfa. No cookie yet.
  if (parsed.mfaRequired) {
    return NextResponse.json({ step: "mfa", mfaToken: parsed.mfaToken, methods: parsed.methods ?? ["totp"] });
  }
  if (parsed.mfaEnrollmentRequired) {
    return NextResponse.json({
      step: "enroll",
      mfaToken: parsed.mfaToken,
      factorId: parsed.factorId,
      secret: parsed.secret,
      otpauthUri: parsed.otpauthUri,
      qrCodeDataUrl: parsed.qrCodeDataUrl,
    });
  }

  // The cookie holds only the auth-server's opaque session token, never the password.
  return sessionResponse(request, parsed);
}
