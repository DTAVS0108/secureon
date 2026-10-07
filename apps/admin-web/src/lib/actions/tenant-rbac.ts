"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/lib/api/server-client";
import type {
  TenantPermission,
  TenantRole,
  TenantRoleDetail,
} from "@/lib/api/tenant-rbac";

function rbacBase(tenantId: string) {
  return `/api/admin/v1/tenants/${tenantId}/tenant-rbac`;
}

function revalidateTenantRoles(tenantId: string) {
  revalidatePath(`/tenants/${tenantId}/roles`);
}

export async function listTenantRolesAction(
  tenantId: string,
  customOnly = true,
): Promise<TenantRole[]> {
  const query = customOnly ? "?customOnly=true" : "";
  return apiFetch<TenantRole[]>(`${rbacBase(tenantId)}/roles${query}`);
}

export async function listTenantPermissionsAction(tenantId: string): Promise<TenantPermission[]> {
  return apiFetch<TenantPermission[]>(`${rbacBase(tenantId)}/permissions`);
}

export async function getTenantRoleAction(
  tenantId: string,
  roleId: string,
): Promise<TenantRoleDetail> {
  return apiFetch<TenantRoleDetail>(`${rbacBase(tenantId)}/roles/${roleId}`);
}

export async function createTenantRoleAction(
  tenantId: string,
  body: {
    name: string;
    description?: string;
    permissionIds: string[];
    applicationIds: string[];
  },
): Promise<TenantRoleDetail> {
  const created = await apiFetch<TenantRoleDetail>(`${rbacBase(tenantId)}/roles`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  revalidateTenantRoles(tenantId);
  return created;
}

export async function updateTenantRoleAction(
  tenantId: string,
  roleId: string,
  body: {
    name: string;
    description?: string;
    permissionIds: string[];
    applicationIds: string[];
  },
): Promise<TenantRoleDetail> {
  const updated = await apiFetch<TenantRoleDetail>(`${rbacBase(tenantId)}/roles/${roleId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  revalidateTenantRoles(tenantId);
  return updated;
}

export async function deleteTenantRoleAction(tenantId: string, roleId: string): Promise<void> {
  await apiFetch<void>(`${rbacBase(tenantId)}/roles/${roleId}`, { method: "DELETE" });
  revalidateTenantRoles(tenantId);
}

export async function createTenantPermissionAction(
  tenantId: string,
  body: { key: string; description?: string },
): Promise<TenantPermission> {
  const created = await apiFetch<TenantPermission>(`${rbacBase(tenantId)}/permissions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  revalidateTenantRoles(tenantId);
  return created;
}

export async function deleteTenantPermissionAction(
  tenantId: string,
  permissionId: string,
): Promise<void> {
  await apiFetch<void>(`${rbacBase(tenantId)}/permissions/${permissionId}`, {
    method: "DELETE",
  });
  revalidateTenantRoles(tenantId);
}
