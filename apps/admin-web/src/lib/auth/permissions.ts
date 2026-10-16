import type { ApplicationContextItem } from "@/lib/api/context";

export const SECTION_PERMISSIONS: Record<string, string> = {
  users: "user:read",
  roles: "role:read",
  groups: "role:read",
  permissions: "role:read",
  settings: "app:read",
  audit: "audit:read",
  logs: "logs:read",
  sessions: "session:read",
};

export function hasConsoleFeature(
  platformSuperAdmin: boolean,
  app: ApplicationContextItem | undefined,
  section: string,
): boolean {
  if (platformSuperAdmin) return true;
  if (!app) return false;
  const features = app.consoleFeatures ?? [];
  if (features.length === 0) return false;
  return features.includes(section);
}

/** App permission held through an app role (accounts without a console role). */
export function hasPermission(
  platformSuperAdmin: boolean,
  app: ApplicationContextItem | undefined,
  permission: string,
): boolean {
  if (platformSuperAdmin) return true;
  if (!app) return false;
  return app.permissions.includes(permission);
}

/** For console operators the role's enabled sections are the whole grant, as on the server. */
export function canAccessSection(
  platformSuperAdmin: boolean,
  app: ApplicationContextItem | undefined,
  section: string,
): boolean {
  if (platformSuperAdmin) return true;
  return hasConsoleFeature(platformSuperAdmin, app, section);
}
