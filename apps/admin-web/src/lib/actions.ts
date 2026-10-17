"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { fetchAdminContext } from "@/lib/api/context";
import {
  createApplicationApi,
  deleteApplicationApi,
  isolateApplicationApi,
  updateApplicationApi,
} from "./api/applications";
import {
  createOAuthClientApi,
  deleteOAuthClientApi,
  updateOAuthClientApi,
} from "./api/oauth-clients";
import {
  createPermissionApi,
  deletePermissionApi,
  seedDefaultPermissionsApi,
  updatePermissionApi,
} from "./api/permissions";
import { createRoleApi, deleteRoleApi, updateRoleApi } from "./api/roles";
import {
  createGroupApi,
  deleteGroupApi,
  updateGroupApi,
} from "./api/groups";
import {
  createTenantApi,
  deleteTenantApi,
  updateTenantApi,
} from "./api/tenants";
import {
  adminSetUserPasswordApi,
  createUserApi,
  deleteUserApi,
  markUserEmailVerifiedApi,
  setUserEmailVerifiedApi,
  resendUserVerificationEmailApi,
  deleteUserMfaFactorApi,
  resetUserMfaApi,
  resetUserMfaMethodApi,
  sendUserPasswordResetEmailApi,
  setUserStatusApi,
  unlockUserApi,
  updateUserApi,
  updateUserAuthMethodsApi,
} from "./api/users";
import { ApiError } from "./api/client";
import { filterValidUuids, isValidUuid } from "./uuid";

function formatApiError(e: ApiError): string {
  const body = e.body;
  if (typeof body === "object" && body !== null) {
    const errors = (body as { errors?: { field?: string; message?: string }[] }).errors;
    if (Array.isArray(errors) && errors.length > 0) {
      return errors
        .map((err) => `${err.field ?? "field"}: ${err.message ?? "invalid"}`)
        .join("; ");
    }
    const detail = (body as { detail?: string }).detail;
    if (detail && detail !== "Validation failed") return detail;
  }
  if (e.message.toLowerCase().includes("not valid")) {
    return "Invalid ID in the request. Refresh the page and try again.";
  }
  return e.message;
}

function actionError(e: unknown): FormState {
  if (e instanceof ApiError) return fail(formatApiError(e));
  if (e instanceof Error) return fail(e.message);
  return fail("Request failed");
}

export type FormState = {
  ok: boolean;
  error?: string;
  createdRoleId?: string;
  createdGroupId?: string;
  createdPermissionId?: string;
  createdUserId?: string;
  createdApplicationId?: string;
  createdClientSecret?: string;
  schemaName?: string;
};

const ok = (extra?: Partial<FormState>): FormState => ({ ok: true, ...extra });
const fail = (error: string): FormState => ({ ok: false, error });

function str(fd: FormData, key: string): string {
  return (fd.get(key) as string | null)?.trim() ?? "";
}

function list(fd: FormData, key: string): string[] {
  // Accept either multiple form entries (checkboxes) or a comma/newline string.
  const all = fd.getAll(key).map((v) => String(v).trim()).filter(Boolean);
  if (all.length > 1) return all;
  const single = all[0] ?? "";
  return single
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function slugify(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

// ---- Tenants ----

export async function tenantCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const name = str(fd, "name");
  if (!name) return fail("Name is required.");
  try {
    await createTenantApi({
      name,
      slug: str(fd, "slug") || slugify(name),
      plan: str(fd, "plan") || "free",
      status: str(fd, "status") || "active",
    });
    revalidatePath("/tenants");
    revalidatePath("/");
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function tenantUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  const name = str(fd, "name");
  if (!id) return fail("Missing tenant id.");
  if (!name) return fail("Name is required.");
  try {
    await updateTenantApi(id, {
      name,
      slug: str(fd, "slug") || slugify(name),
      plan: str(fd, "plan") || "free",
      status: str(fd, "status") || "active",
    });
    revalidatePath("/tenants");
    revalidatePath(`/tenants/${id}`);
    revalidatePath("/");
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function tenantDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  try {
    if (id) await deleteTenantApi(id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the tenant." };
  }
  revalidatePath("/tenants");
  revalidatePath("/");
  redirect("/tenants");
}

// ---- Applications (product) ----

function applicationProductWritePayload(fd: FormData) {
  return {
    name: str(fd, "name"),
    description: str(fd, "description"),
    slug: str(fd, "slug"),
    status: str(fd, "status") || "active",
  };
}

export async function applicationWithClientCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const name = str(fd, "name");
  const tenantId = str(fd, "tenantId");
  if (!name) return fail("Application name is required.");
  if (!tenantId) return fail("Tenant is required.");
  try {
    const application = await createApplicationApi({
      name,
      tenantId,
      slug: str(fd, "slug") || slugify(name),
      description: str(fd, "description"),
      status: str(fd, "status") || "active",
    });
    const { client, clientSecret } = await createOAuthClientApi({
      ...oauthClientWritePayload(fd),
      applicationId: application.id,
      clientId: str(fd, "clientId") || application.slug,
    });
    revalidatePath("/applications");
    revalidatePath("/app");
    revalidatePath("/tenants");
    revalidatePath("/audit");
    return ok({
      createdApplicationId: application.id,
      createdClientSecret: clientSecret,
    });
  } catch (e) {
    return actionError(e);
  }
}

export async function applicationProductCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const name = str(fd, "name");
  const tenantId = str(fd, "tenantId");
  if (!name) return fail("Name is required.");
  if (!tenantId) return fail("Tenant is required.");
  try {
    const application = await createApplicationApi({
      ...applicationProductWritePayload(fd),
      tenantId,
    });
    revalidatePath("/applications");
    revalidatePath("/app");
    revalidatePath("/tenants");
    return ok({ createdApplicationId: application.id });
  } catch (e) {
    return actionError(e);
  }
}

export async function applicationProductUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  if (!id) return fail("Missing application id.");
  const name = str(fd, "name");
  if (!name) return fail("Name is required.");
  try {
    await updateApplicationApi(id, applicationProductWritePayload(fd));
    revalidatePath("/applications");
    revalidatePath(`/applications/${id}`);
    revalidatePath("/app");
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function applicationProductDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  try {
    if (id) await deleteApplicationApi(id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the application." };
  }
  revalidatePath("/applications");
  revalidatePath("/app");
  revalidatePath("/audit");
  const ctx = await fetchAdminContext().catch(() => null);
  redirect(ctx && !ctx.platformSuperAdmin ? (ctx.connectAppEnabled !== false ? "/connect" : "/app") : "/applications");
}

export async function applicationIsolateAction(fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  if (!id) return fail("Missing application id.");
  try {
    const application = await isolateApplicationApi(id);
    revalidatePath("/applications");
    revalidatePath(`/applications/${id}`);
    revalidatePath(`/app/${id}`);
    revalidatePath("/app");
    return ok({ schemaName: application.schemaName ?? undefined });
  } catch (e) {
    return actionError(e);
  }
}

// ---- OAuth clients ----

function oauthClientWritePayload(fd: FormData) {
  return {
    type: str(fd, "type") || "web",
    status: str(fd, "status") || "active",
    grantTypes: list(fd, "grantTypes"),
    scopes: list(fd, "scopes"),
    redirectUris: list(fd, "redirectUris"),
    postLogoutRedirectUris: list(fd, "postLogoutRedirectUris"),
    pkceRequired: fd.get("pkceRequired") === "on",
    tokenEndpointAuthMethod: str(fd, "tokenEndpointAuthMethod"),
    subjectType: str(fd, "subjectType") || undefined,
  };
}

export async function oauthClientCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const applicationId = str(fd, "applicationId");
  const tenantId = str(fd, "tenantId");
  if (!applicationId && !tenantId) {
    return fail("Tenant is required when auto-creating an application.");
  }
  try {
    const payload = oauthClientWritePayload(fd);
    const { client, clientSecret } = await createOAuthClientApi({
      ...payload,
      applicationId: applicationId || undefined,
      applicationName: str(fd, "applicationName") || undefined,
      tenantId: tenantId || undefined,
      clientId: str(fd, "clientId"),
    });
    revalidatePath("/applications");
    revalidatePath("/app");
    revalidatePath("/tenants");
    revalidatePath("/audit");
    return ok({
      createdApplicationId: client.applicationId,
      createdClientSecret: clientSecret,
    });
  } catch (e) {
    return actionError(e);
  }
}

export async function oauthClientUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  if (!id) return fail("Missing OAuth client id.");
  try {
    await updateOAuthClientApi(id, oauthClientWritePayload(fd));
    revalidatePath("/applications");
    revalidatePath(`/applications/${id}`);
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function oauthClientDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  try {
    if (id) await deleteOAuthClientApi(id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the OAuth client." };
  }
  revalidatePath("/applications");
  revalidatePath("/app");
  revalidatePath("/audit");
  redirect("/applications");
}

// ---- Users ----

function revalidateUserPaths(paths: {
  tenantId?: string;
  applicationId?: string;
  userId?: string;
} = {}) {
  revalidatePath("/users");
  revalidatePath("/tenants");
  if (paths.tenantId) {
    revalidatePath(`/tenants/${paths.tenantId}`);
  }
  if (paths.applicationId) {
    revalidatePath(`/app/${paths.applicationId}/users`);
  }
  if (paths.userId) {
    revalidatePath(`/users/${paths.userId}`);
  }
}

export async function userCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const email = str(fd, "email");
  const tenantId = str(fd, "tenantId");
  const applicationId = str(fd, "applicationId");
  if (!email) return fail("Email is required.");
  if (!tenantId) return fail("Tenant is required.");
  if (applicationId && !isValidUuid(applicationId)) {
    return fail("Invalid application. Refresh and try again.");
  }
  try {
    const created = await createUserApi({
      email,
      tenantId,
      applicationId: applicationId || undefined,
      username: str(fd, "username") || email.split("@")[0],
      firstName: str(fd, "firstName"),
      lastName: str(fd, "lastName"),
      status: str(fd, "status") || "invited",
      roleIds: list(fd, "roleIds"),
    });
    revalidateUserPaths({ tenantId, applicationId, userId: created.id });
    return ok({ createdUserId: created.id });
  } catch (e) {
    return actionError(e);
  }
}

export async function userUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  if (!id) return fail("Missing user id.");
  const email = str(fd, "email");
  if (!email) return fail("Email is required.");
  const applicationId = str(fd, "applicationId");
  const emailVerified = fd.get("emailVerified") === "true";
  try {
    await updateUserApi(id, {
      email,
      username: str(fd, "username"),
      firstName: str(fd, "firstName"),
      lastName: str(fd, "lastName"),
      status: str(fd, "status") || "active",
      emailVerified,
      roleIds: list(fd, "roleIds"),
    }, applicationId || undefined);
    // Dedicated endpoint (same as Security tab) — PUT emailVerified alone is easy to miss on older servers.
    await setUserEmailVerifiedApi(id, emailVerified, applicationId || undefined);
    revalidateUserPaths({ applicationId, userId: id });
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function userDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  try {
    if (id) await deleteUserApi(id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the user." };
  }
  revalidateUserPaths({ applicationId });
  if (applicationId) {
    redirect(`/app/${applicationId}/users`);
  } else {
    redirect("/users");
  }
}

export async function userResetMfaAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await resetUserMfaApi(id);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userUpdateAuthMethodsAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  const methodsJson = str(fd, "methods");
  if (!id || !applicationId || !methodsJson) return;
  const methods = JSON.parse(methodsJson) as Record<string, boolean>;
  await updateUserAuthMethodsApi(applicationId, id, methods);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userDeleteMfaFactorAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  const factorId = str(fd, "factorId");
  if (id && applicationId && factorId) {
    await deleteUserMfaFactorApi(applicationId, id, factorId);
    revalidateUserPaths({ applicationId, userId: id });
  }
}

export async function userResetMfaMethodAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  const methodId = str(fd, "methodId");
  if (id && applicationId && methodId) {
    await resetUserMfaMethodApi(applicationId, id, methodId);
    revalidateUserPaths({ applicationId, userId: id });
  }
}

export async function userSetStatusAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const status = str(fd, "status");
  const applicationId = str(fd, "applicationId");
  if (id && status) await setUserStatusApi(id, status);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userUnlockAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await unlockUserApi(id);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userAdminSetPasswordAction(
  _prev: FormState,
  fd: FormData,
): Promise<FormState> {
  const id = str(fd, "id");
  const password = str(fd, "password");
  const applicationId = str(fd, "applicationId");
  if (!id) return fail("Missing user id.");
  if (!password || password.length < 8) {
    return fail("Password must be at least 8 characters.");
  }
  try {
    await adminSetUserPasswordApi(id, password, applicationId || undefined);
    revalidateUserPaths({ applicationId, userId: id });
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function userRemovePasswordAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (!id) return;
  const { removeUserPasswordApi } = await import("./api/users");
  await removeUserPasswordApi(id, applicationId || undefined);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userSendPasswordResetEmailAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await sendUserPasswordResetEmailApi(id, applicationId || undefined);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userResendVerificationEmailAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await resendUserVerificationEmailApi(id, applicationId || undefined);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userMarkEmailVerifiedAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await markUserEmailVerifiedApi(id, applicationId || undefined);
  revalidateUserPaths({ applicationId, userId: id });
}

export async function userSetEmailUnverifiedAction(fd: FormData): Promise<void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (id) await setUserEmailVerifiedApi(id, false, applicationId || undefined);
  revalidateUserPaths({ applicationId, userId: id });
}

// ---- Roles ----

function roleIdsFromForm(fd: FormData, key: string): string[] {
  return filterValidUuids(fd.getAll(key).map((v) => String(v)));
}

function revalidateRolePaths(applicationId?: string) {
  revalidatePath("/roles");
  if (applicationId) {
    revalidatePath(`/app/${applicationId}/roles`);
  }
}

export async function roleCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const name = str(fd, "name");
  const tenantId = str(fd, "tenantId");
  const applicationId = str(fd, "applicationId");
  if (!name) return fail("Name is required.");
  if (!tenantId) return fail("Tenant is required.");
  if (!applicationId) return fail("Application is required.");
  if (!isValidUuid(tenantId) || !isValidUuid(applicationId)) {
    return fail("Invalid tenant or application. Refresh the page and try again.");
  }
  try {
    const created = await createRoleApi({
      name,
      tenantId,
      applicationId,
      description: str(fd, "description"),
      isComposite: str(fd, "isComposite") === "on",
      permissionIds: roleIdsFromForm(fd, "permissionIds"),
      childRoleIds: roleIdsFromForm(fd, "childRoleIds"),
    });
    revalidateRolePaths(applicationId);
    return ok({ createdRoleId: created.id });
  } catch (e) {
    return actionError(e);
  }
}

export async function roleUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (!id) return fail("Missing role id.");
  if (!isValidUuid(id)) return fail("Invalid role id.");
  const name = str(fd, "name");
  if (!name) return fail("Name is required.");
  if (!applicationId || !isValidUuid(applicationId)) {
    return fail("Invalid application. Refresh the page and try again.");
  }
  try {
    await updateRoleApi(
      id,
      {
        name,
        description: str(fd, "description"),
        isComposite: str(fd, "isComposite") === "on",
        permissionIds: roleIdsFromForm(fd, "permissionIds"),
        childRoleIds: roleIdsFromForm(fd, "childRoleIds"),
      },
      applicationId,
    );
    revalidateRolePaths(applicationId);
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function roleDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (!id || !isValidUuid(id)) return { error: "Invalid role id." };
  if (!applicationId || !isValidUuid(applicationId)) return { error: "Invalid application id." };
  try {
    await deleteRoleApi(id, applicationId);
  } catch (e) {
    // e.g. 409 "Remove all user assignments before deleting this role."
    return { error: e instanceof Error ? e.message : "Could not delete the role." };
  }
  revalidateRolePaths(applicationId);
  redirect(`/app/${applicationId}/roles`);
}

// ---- Groups ----

function revalidateGroupPaths(applicationId?: string) {
  if (applicationId) {
    revalidatePath(`/app/${applicationId}/groups`);
    revalidatePath(`/app/${applicationId}/roles`);
  }
}

function idsFromForm(fd: FormData, key: string): string[] {
  return filterValidUuids(list(fd, key));
}

export async function groupCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const name = str(fd, "name");
  const tenantId = str(fd, "tenantId");
  const applicationId = str(fd, "applicationId");
  if (!name) return fail("Name is required.");
  if (!tenantId || !applicationId) return fail("Tenant and application are required.");
  if (!isValidUuid(tenantId) || !isValidUuid(applicationId)) {
    return fail("Invalid tenant or application. Refresh the page and try again.");
  }
  try {
    const created = await createGroupApi({
      name,
      tenantId,
      applicationId,
      description: str(fd, "description"),
      roleIds: idsFromForm(fd, "roleIds"),
      memberUserIds: idsFromForm(fd, "memberUserIds"),
    });
    revalidateGroupPaths(applicationId);
    return ok({ createdGroupId: created.id });
  } catch (e) {
    return actionError(e);
  }
}

export async function groupUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  const name = str(fd, "name");
  if (!id || !isValidUuid(id)) return fail("Missing group id.");
  if (!name) return fail("Name is required.");
  if (!applicationId || !isValidUuid(applicationId)) {
    return fail("Invalid application. Refresh the page and try again.");
  }
  try {
    await updateGroupApi(applicationId, id, {
      name,
      description: str(fd, "description"),
      roleIds: idsFromForm(fd, "roleIds"),
      memberUserIds: idsFromForm(fd, "memberUserIds"),
    });
    revalidateGroupPaths(applicationId);
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function groupDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (!id || !isValidUuid(id)) return { error: "Invalid group id." };
  if (!applicationId || !isValidUuid(applicationId)) return { error: "Invalid application id." };
  try {
    await deleteGroupApi(applicationId, id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the group." };
  }
  revalidateGroupPaths(applicationId);
  redirect(`/app/${applicationId}/groups`);
}

// ---- Permissions (catalog ↔ `permission` table) ----

function revalidatePermissionPaths(applicationId: string) {
  revalidatePath(`/app/${applicationId}/permissions`);
  revalidatePath(`/app/${applicationId}/roles`);
}

export async function seedDefaultPermissionsAction(
  applicationId: string,
): Promise<{ ok: boolean; error?: string; created?: number }> {
  if (!isValidUuid(applicationId)) {
    return { ok: false, error: "Invalid application id." };
  }
  try {
    const result = await seedDefaultPermissionsApi(applicationId);
    revalidatePermissionPaths(applicationId);
    return { ok: true, created: result.created };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: formatApiError(e) };
    if (e instanceof Error) return { ok: false, error: e.message };
    return { ok: false, error: "Request failed" };
  }
}

export async function permissionCreateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const applicationId = str(fd, "applicationId");
  const key = str(fd, "key").trim().toLowerCase();
  const description = str(fd, "description");
  if (!applicationId || !isValidUuid(applicationId)) {
    return fail("Invalid application. Refresh the page and try again.");
  }
  if (!key) return fail("Key is required (e.g. user:read).");
  if (!/^[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*$/.test(key)) {
    return fail("Key must be resource:action (lowercase letters, numbers, hyphens).");
  }
  try {
    const created = await createPermissionApi(applicationId, { key, description });
    revalidatePermissionPaths(applicationId);
    return ok({ createdPermissionId: created.id });
  } catch (e) {
    return actionError(e);
  }
}

export async function permissionUpdateAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  const key = str(fd, "key").trim().toLowerCase();
  const description = str(fd, "description");
  if (!id || !isValidUuid(id)) return fail("Invalid permission id.");
  if (!applicationId || !isValidUuid(applicationId)) {
    return fail("Invalid application. Refresh the page and try again.");
  }
  if (key && !/^[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*$/.test(key)) {
    return fail("Key must be resource:action (lowercase).");
  }
  try {
    await updatePermissionApi(
      applicationId,
      id,
      { key: key || undefined, description },
    );
    revalidatePermissionPaths(applicationId);
    return ok();
  } catch (e) {
    return actionError(e);
  }
}

export async function permissionDeleteAction(fd: FormData): Promise<{ error?: string } | void> {
  const id = str(fd, "id");
  const applicationId = str(fd, "applicationId");
  if (!id || !isValidUuid(id)) return { error: "Invalid permission id" };
  if (!applicationId || !isValidUuid(applicationId)) return { error: "Invalid application id" };
  try {
    await deletePermissionApi(applicationId, id);
  } catch (e) {
    return { error: e instanceof Error ? e.message : "Could not delete the permission." };
  }
  revalidatePermissionPaths(applicationId);
  redirect(`/app/${applicationId}/permissions`);
}
