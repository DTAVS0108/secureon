import { ApiError } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import type { ApplicationProduct, Status } from "@/lib/types";

type ApplicationDto = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description?: string | null;
  status: string;
  schemaName?: string | null;
  oauthClientCount?: number;
  createdAt: string;
  updatedAt: string;
};

function mapApplication(dto: ApplicationDto): ApplicationProduct {
  return {
    id: dto.id,
    tenantId: dto.tenantId,
    name: dto.name,
    slug: dto.slug,
    description: dto.description,
    status: dto.status as Status,
    schemaName: dto.schemaName ?? null,
    oauthClientCount: dto.oauthClientCount ?? 0,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt ?? dto.createdAt,
  };
}

export async function listApplications(tenantId?: string): Promise<ApplicationProduct[]> {
  const path = tenantId
    ? `/api/admin/v1/applications?tenantId=${tenantId}`
    : "/api/admin/v1/applications";
  return (await apiFetch<ApplicationDto[]>(path)).map(mapApplication);
}

export async function getApplication(id: string): Promise<ApplicationProduct | null> {
  try {
    return mapApplication(await apiFetch<ApplicationDto>(`/api/admin/v1/applications/${id}`));
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export type ApplicationWriteInput = {
  name: string;
  description?: string;
  slug?: string;
  status: string;
};

export async function createApplicationApi(
  input: ApplicationWriteInput & { tenantId: string },
): Promise<ApplicationProduct> {
  return mapApplication(
    await apiFetch<ApplicationDto>("/api/admin/v1/applications", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  );
}

export async function updateApplicationApi(
  id: string,
  input: ApplicationWriteInput,
): Promise<ApplicationProduct> {
  return mapApplication(
    await apiFetch<ApplicationDto>(`/api/admin/v1/applications/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  );
}

/**
 * Deletes an application: `confirm` is its slug typed again, and the operator's second step must be fresh (the
 * browser asks for it). An application with OAuth clients needs a second admin: `pending` is then true and the
 * request waits on Access review.
 */
export async function deleteApplicationApi(id: string, confirm: string): Promise<{ pending: boolean }> {
  const res = await apiFetch<{ id?: string; status?: string } | undefined>(
    `/api/admin/v1/applications/${id}?confirm=${encodeURIComponent(confirm)}`,
    { method: "DELETE" },
  );
  return { pending: Boolean(res && res.status === "PENDING") };
}

export async function isolateApplicationApi(id: string): Promise<ApplicationProduct> {
  return mapApplication(
    await apiFetch<ApplicationDto>(`/api/admin/v1/applications/${id}/isolate`, {
      method: "POST",
    }),
  );
}
