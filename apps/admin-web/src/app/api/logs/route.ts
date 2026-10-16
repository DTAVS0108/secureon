// Calls below run as the signed-in user; this import registers that lookup for route handlers.
import "@/lib/api/server-auth";
import { deleteApplicationLogs, listApplicationLogs, type LogQuery } from "@/lib/api/logs";
import { ApiError } from "@/lib/api/http";
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

function parseQuery(searchParams: URLSearchParams): LogQuery {
  return {
    sessionId: searchParams.get("sessionId") ?? undefined,
    requestId: searchParams.get("requestId") ?? undefined,
    level: searchParams.get("level") ?? undefined,
    applicationId: searchParams.get("applicationId") ?? undefined,
    tenantId: searchParams.get("tenantId") ?? undefined,
    since: searchParams.get("since") ?? undefined,
    until: searchParams.get("until") ?? undefined,
    search: searchParams.get("search") ?? undefined,
    limit: searchParams.get("limit") ? Number(searchParams.get("limit")) : undefined,
    includeUnscoped: searchParams.get("includeUnscoped") !== "false",
  };
}

/** Keep the upstream status (403, 400…) so the viewer can tell "not allowed" from "server error". */
function errorStatus(e: unknown): number {
  return e instanceof ApiError && e.status >= 400 ? e.status : 500;
}

export async function GET(request: Request) {
  try {
    const logs = await listApplicationLogs(parseQuery(new URL(request.url).searchParams));
    return NextResponse.json(logs);
  } catch (e) {
    const message = e instanceof Error ? e.message : "Failed to load logs";
    return NextResponse.json({ error: message }, { status: errorStatus(e) });
  }
}

export async function DELETE(request: Request) {
  const query = parseQuery(new URL(request.url).searchParams);
  if (!query.since || !query.until) {
    return NextResponse.json({ error: "since and until are required for delete" }, { status: 400 });
  }
  try {
    const result = await deleteApplicationLogs({
      ...query,
      since: query.since,
      until: query.until,
    });
    return NextResponse.json(result);
  } catch (e) {
    const message = e instanceof Error ? e.message : "Failed to delete logs";
    return NextResponse.json({ error: message }, { status: errorStatus(e) });
  }
}
