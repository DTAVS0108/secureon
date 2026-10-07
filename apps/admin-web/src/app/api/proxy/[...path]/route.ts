import { NextResponse } from "next/server";
import { AUTH_SERVER_URL } from "@/lib/config";
import { adminAuthHeader } from "@/lib/api/server-auth";

const ACT_AS_EMAIL = process.env.SECUREONE_ACT_AS_EMAIL;

async function forward(request: Request, pathSegments: string[]) {
  const auth = await adminAuthHeader();
  if (!auth) {
    return NextResponse.json({ detail: "Not signed in." }, { status: 401 });
  }

  const incoming = new URL(request.url);
  const target = new URL(`${AUTH_SERVER_URL}/${pathSegments.join("/")}`);
  target.search = incoming.search;

  const headers = new Headers();
  headers.set("Authorization", auth);
  const accept = request.headers.get("accept");
  headers.set("Accept", accept ?? "application/json");
  if (ACT_AS_EMAIL) headers.set("X-Act-As-Email", ACT_AS_EMAIL);
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("Content-Type", contentType);
  const appId = request.headers.get("x-application-id");
  if (appId) headers.set("X-Application-Id", appId);

  const isMultipart = contentType?.includes("multipart/form-data") ?? false;
  const body =
    request.method === "GET" || request.method === "HEAD"
      ? undefined
      : isMultipart
        ? await request.arrayBuffer()
        : await request.text();

  const res = await fetch(target, {
    method: request.method,
    headers,
    body,
    cache: "no-store",
  });

  const outHeaders = new Headers();
  const resContentType = res.headers.get("Content-Type");
  if (resContentType) outHeaders.set("Content-Type", resContentType);
  const disposition = res.headers.get("Content-Disposition");
  if (disposition) outHeaders.set("Content-Disposition", disposition);

  // 204/205 must not include a body — NextResponse throws if a buffer is attached.
  if (res.status === 204 || res.status === 205) {
    return new NextResponse(null, { status: res.status, headers: outHeaders });
  }

  if (resContentType?.includes("application/json") || resContentType?.includes("text/")) {
    const text = await res.text();
    return new NextResponse(text || null, { status: res.status, headers: outHeaders });
  }

  const buffer = await res.arrayBuffer();
  return new NextResponse(buffer, { status: res.status, headers: outHeaders });
}

type RouteContext = { params: Promise<{ path: string[] }> };

export async function GET(request: Request, context: RouteContext) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function POST(request: Request, context: RouteContext) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function PUT(request: Request, context: RouteContext) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function PATCH(request: Request, context: RouteContext) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function DELETE(request: Request, context: RouteContext) {
  const { path } = await context.params;
  return forward(request, path);
}
