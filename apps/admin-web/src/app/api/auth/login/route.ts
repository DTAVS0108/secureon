import { NextResponse } from "next/server";
import { authServerInternalUrl } from "@/lib/config";
import { forwardedHeadersFrom } from "@/lib/api/server-auth";
import { readJson, sessionResponse } from "@/lib/auth/session-cookie";

type LoginResult = {
  detail?: string;
  sessionToken?: string;
  expiresIn?: number;
  mfaRequired?: boolean;
  mfaEnrollmentRequired?: boolean;
  mfaToken?: string;
  methods?: string[];
  tryAnotherWay?: boolean;
  factorId?: string;
  secret?: string;
  otpauthUri?: string;
  qrCodeDataUrl?: string;
  passwordChangeRequired?: boolean;
  changeToken?: string;
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

  const res = await fetch(`${authServerInternalUrl()}/api/admin/v1/auth/login`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      // Per-client sign-in rate limit and audit IP (single image only; see server-auth.ts).
      ...forwardedHeadersFrom(request.headers),
    },
    body: JSON.stringify({
      tenantSlug: body.tenantSlug?.trim() || undefined,
      emailOrUsername,
      password,
    }),
    cache: "no-store",
  });

  const parsed = await readJson<LoginResult>(res);
  // A temporary password (the bootstrap admin's, or one an administrator set): choose a new one first.
  if (res.status === 403 && parsed.passwordChangeRequired && parsed.changeToken) {
    return NextResponse.json({ step: "new-password", changeToken: parsed.changeToken });
  }
  if (!res.ok) {
    return NextResponse.json({ detail: parsed.detail ?? "Invalid credentials." }, { status: res.status });
  }

  // Password accepted; the second step happens at /api/auth/login/mfa. No cookie yet.
  if (parsed.mfaRequired) {
    return NextResponse.json({
      step: "mfa",
      mfaToken: parsed.mfaToken,
      methods: parsed.methods ?? ["totp"],
      tryAnotherWay: parsed.tryAnotherWay !== false,
    });
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
