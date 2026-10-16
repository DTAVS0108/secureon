import { NextResponse } from "next/server";
import { authServerInternalUrl } from "@/lib/config";
import { forwardedHeadersFrom } from "@/lib/api/server-auth";

// First-run setup: forwards to the auth-server's public /api/v1/setup API (no console session yet).
const ACTIONS = new Set(["status", "verify", "complete"]);

async function forward(request: Request, action: string) {
  if (!ACTIONS.has(action)) return NextResponse.json({ detail: "Not found." }, { status: 404 });
  const res = await fetch(`${authServerInternalUrl()}/api/v1/setup/${action}`, {
    method: request.method,
    headers: {
      Accept: "application/json",
      ...(request.method === "POST" ? { "Content-Type": "application/json" } : {}),
      // Wrong-code attempts are counted per client address.
      ...forwardedHeadersFrom(request.headers),
    },
    body: request.method === "POST" ? await request.text() : undefined,
    cache: "no-store",
  });
  const text = await res.text();
  return new NextResponse(text || null, {
    status: res.status,
    headers: { "Content-Type": res.headers.get("Content-Type") ?? "application/json" },
  });
}

type Context = { params: Promise<{ action: string }> };

export async function GET(request: Request, context: Context) {
  return forward(request, (await context.params).action);
}

export async function POST(request: Request, context: Context) {
  return forward(request, (await context.params).action);
}
