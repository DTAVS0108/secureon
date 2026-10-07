export type NavItem = {
  label: string;
  href: string;
  description: string;
  superAdminOnly?: boolean;
  /** Open in a new browser tab (e.g. documentation). */
  openInNewTab?: boolean;
};

export type SidebarNavChild = {
  label: string;
  href: string;
  isActive?: (pathname: string) => boolean;
};

export type SidebarNavGroupConfig = {
  label: string;
  children: SidebarNavChild[];
};

export function isTenantListNavActive(pathname: string): boolean {
  if (pathname === "/tenants") return true;
  const match = pathname.match(/^\/tenants\/([^/]+)(?:\/.*)?$/);
  if (!match) return false;
  if (match[1] === "roles") return false;
  return !pathname.includes("/roles");
}

export function isTenantRolesNavActive(pathname: string): boolean {
  return pathname === "/tenants/roles" || /^\/tenants\/[^/]+\/roles\/?$/.test(pathname);
}

/** Collapsible tenant section for platform super-admin sidebars. */
export const TENANT_NAV_GROUP: SidebarNavGroupConfig = {
  label: "Tenant",
  children: [
    { label: "Tenants", href: "/tenants", isActive: isTenantListNavActive },
    { label: "Roles & Permissions", href: "/tenants/roles", isActive: isTenantRolesNavActive },
  ],
};

export function isAccessReviewNavActive(pathname: string): boolean {
  return pathname === "/access-review" || /^\/tenants\/[^/]+\/access-review\/?$/.test(pathname);
}

/** Tenant super-admin: scoped links to their organization only. */
export function tenantNavGroupForOperator(tenantId: string): SidebarNavGroupConfig {
  return {
    label: "Tenant",
    children: [
      { label: "Tenants", href: `/tenants/${tenantId}`, isActive: isTenantListNavActive },
      {
        label: "Roles & Permissions",
        href: `/tenants/${tenantId}/roles`,
        isActive: isTenantRolesNavActive,
      },
      {
        label: "Access review",
        href: `/tenants/${tenantId}/access-review`,
        isActive: isAccessReviewNavActive,
      },
    ],
  };
}

/** Super-admin / platform operations (shown at bottom of sidebar). */
export const SUPER_ADMIN_NAV: NavItem[] = [
  {
    label: "OAuth clients",
    href: "/applications",
    description: "Enterprise client registry",
    superAdminOnly: true,
  },
  {
    label: "SecureOne Confluence",
    href: "/confluence",
    description: "Platform & integration documentation",
    superAdminOnly: true,
    openInNewTab: true,
  },
  { label: "Platform settings", href: "/settings", description: "Global defaults", superAdminOnly: true },
  {
    label: "Access review",
    href: "/access-review",
    description: "Platform operators and who holds privileged roles",
    superAdminOnly: true,
  },
];

/** Tenant operator nav — application console + documentation. */
export const TENANT_OPERATOR_NAV: NavItem[] = [
  { label: "Application console", href: "/app", description: "Manage users, roles, and settings per application" },
  {
    label: "SecureOne Confluence",
    href: "/confluence",
    description: "Setup, architecture, API integration",
    openInNewTab: true,
  },
];

/** Legacy platform nav (picker + super admin) — used when no application context. */
export const PLATFORM_NAV: NavItem[] = [
  { label: "Application console", href: "/app", description: "Users, roles, settings — use header dropdown to switch app" },
  ...SUPER_ADMIN_NAV,
];

export function appNav(applicationId: string): NavItem[] {
  const base = `/app/${applicationId}`;
  return [
    { label: "Users", href: `${base}/users`, description: "Members of this application" },
    { label: "Groups", href: `${base}/groups`, description: "Role bundles assigned to many users" },
    { label: "Roles", href: `${base}/roles`, description: "RBAC roles for this application" },
    { label: "Permissions", href: `${base}/permissions`, description: "Permission catalog and role assignment" },
    { label: "Settings", href: `${base}/settings`, description: "Auth & notifications overrides" },
    { label: "Audit log", href: `${base}/audit`, description: "Events for this application" },
    { label: "Application logs", href: `${base}/logs`, description: "Runtime logs — search by session or request" },
    { label: "Sessions", href: `${base}/sessions`, description: "Sign-ins for this application" },
  ];
}
