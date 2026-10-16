import { browserApiFetch as apiFetch } from "./browser-client";
import type { Tenant } from "@/lib/types";

type TenantDto = {
  id: string;
  name: string;
  slug: string;
  status: string;
  plan: string;
  userCount: number;
  appCount: number;
  createdAt: string;
  system?: boolean;
};

function mapTenant(dto: TenantDto): Tenant {
  return {
    id: dto.id,
    name: dto.name,
    slug: dto.slug,
    status: dto.status as Tenant["status"],
    plan: dto.plan as Tenant["plan"],
    userCount: dto.userCount,
    appCount: dto.appCount,
    createdAt: dto.createdAt,
    system: dto.system === true,
  };
}

export async function listTenants(): Promise<Tenant[]> {
  const rows = await apiFetch<TenantDto[]>("/api/admin/v1/tenants");
  return rows.map(mapTenant);
}

export async function getTenant(id: string): Promise<Tenant | null> {
  try {
    const dto = await apiFetch<TenantDto>(`/api/admin/v1/tenants/${id}`);
    return mapTenant(dto);
  } catch (e) {
    if (e instanceof Error && "status" in e && (e as { status: number }).status === 404) {
      return null;
    }
    throw e;
  }
}

export async function createTenantApi(input: {
  name: string;
  slug?: string;
  plan: string;
  status: string;
}): Promise<Tenant> {
  const dto = await apiFetch<TenantDto>("/api/admin/v1/tenants", {
    method: "POST",
    body: JSON.stringify({
      name: input.name,
      slug: input.slug || undefined,
      plan: input.plan,
      status: input.status.toUpperCase(),
    }),
  });
  return mapTenant(dto);
}

export async function updateTenantApi(
  id: string,
  input: { name: string; slug?: string; plan: string; status: string },
): Promise<Tenant> {
  const dto = await apiFetch<TenantDto>(`/api/admin/v1/tenants/${id}`, {
    method: "PUT",
    body: JSON.stringify({
      name: input.name,
      slug: input.slug || undefined,
      plan: input.plan,
      status: input.status.toUpperCase(),
    }),
  });
  return mapTenant(dto);
}

export async function deleteTenantApi(id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/tenants/${id}`, { method: "DELETE" });
}
