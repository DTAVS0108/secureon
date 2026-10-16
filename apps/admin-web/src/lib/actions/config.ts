"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/lib/api/server-client";
import { ApiError } from "@/lib/api/http";
import { PLATFORM_EXPORT } from "@/lib/config-export";

export type ConfigChange = { action: "create" | "update" | "delete"; kind: string; target: string; details: string[] };

export type ConfigResult = {
  dryRun: boolean;
  applied: boolean;
  prune: boolean;
  tenant: string | null;
  errors: string[];
  changes: ConfigChange[];
  /** Credentials generated during apply (OAuth client, webhook, API key, SCIM token): shown once. */
  secrets: { kind: string; name: string; secret: string }[];
  notes: string[];
};

export type ConfigActionResult = { ok: true; result: ConfigResult } | { ok: false; error: string };

/** A tenant's (or the platform's) configuration file (YAML), as the auth-server writes it. */
export async function exportConfigAction(tenantSlug: string): Promise<{ ok: true; yaml: string } | { ok: false; error: string }> {
  try {
    const path = tenantSlug === PLATFORM_EXPORT
      ? "/api/admin/v1/config/platform/export"
      : `/api/admin/v1/config/tenants/${encodeURIComponent(tenantSlug)}/export`;
    const yaml = await apiFetch<string>(path, {
      headers: { Accept: "application/yaml, application/json" },
    });
    return { ok: true, yaml: typeof yaml === "string" ? yaml : JSON.stringify(yaml, null, 2) };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : "Export failed" };
  }
}

/** Dry run (default) or apply. Problems in the file come back as result.errors, not as a thrown error. */
export async function importConfigAction(text: string, options: { apply: boolean; prune: boolean }): Promise<ConfigActionResult> {
  const query = new URLSearchParams({ dryRun: String(!options.apply), prune: String(options.prune) });
  try {
    const result = await apiFetch<ConfigResult>(`/api/admin/v1/config/import?${query}`, {
      method: "POST",
      body: text,
      headers: { "Content-Type": "application/yaml" },
    });
    if (options.apply) revalidateConsole();
    return { ok: true, result };
  } catch (e) {
    if (e instanceof ApiError && e.status === 422 && isResult(e.body)) {
      if (options.apply && e.body.changes.length > 0) revalidateConsole();
      return { ok: true, result: e.body };
    }
    return { ok: false, error: e instanceof Error ? e.message : "The file could not be read" };
  }
}

function revalidateConsole() {
  revalidatePath("/tenants");
  revalidatePath("/applications");
  revalidatePath("/app", "layout");
}

function isResult(body: unknown): body is ConfigResult {
  return typeof body === "object" && body !== null && Array.isArray((body as ConfigResult).changes);
}
