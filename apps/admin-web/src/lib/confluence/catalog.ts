export type ConfluencePage = {
  slug: string;
  title: string;
  /** Path relative to repo `docs/` root */
  file: string;
  description?: string;
};

export type ConfluenceCategory = {
  id: string;
  label: string;
  description?: string;
  pages: ConfluencePage[];
};

/** Documentation catalog grouped by category (aligned with docs/README.md). */
export const CONFLUENCE_CATEGORIES: ConfluenceCategory[] = [
  {
    id: "getting-started",
    label: "Getting started",
    pages: [
      { slug: "home", title: "Documentation hub", file: "README.md", description: "Start here" },
      {
        slug: "operator-getting-started",
        title: "Operator getting started",
        file: "22-operator-getting-started.md",
        description: "Install, setup, and run SecureOne",
      },
    ],
  },
  {
    id: "user-guide",
    label: "User guide",
    description: "The console page by page: every button, form and field",
    pages: [
      { slug: "user-guide", title: "User guide", file: "user-guide/README.md", description: "Roles, chapters and conventions" },
      {
        slug: "guide-sign-in",
        title: "Sign-in, setup and My profile",
        file: "user-guide/01-sign-in-and-profile.md",
        description: "First-run setup, sign-in tabs, authenticator, recovery codes",
      },
      {
        slug: "guide-console-layout",
        title: "Console layout and navigation",
        file: "user-guide/02-console-layout.md",
        description: "Top bar, sidebars, what each role sees",
      },
      {
        slug: "guide-connect-an-app",
        title: "Connect an app",
        file: "user-guide/03-connect-an-app.md",
        description: "The guided wizard, field by field",
      },
      {
        slug: "guide-oauth-clients",
        title: "OAuth clients",
        file: "user-guide/04-oauth-clients.md",
        description: "Register application & OAuth client, templates, grants, client page",
      },
      {
        slug: "guide-tenants",
        title: "Tenants",
        file: "user-guide/05-tenants.md",
        description: "Tenant page panels, roster, console access, tenant roles",
      },
      {
        slug: "guide-application-console",
        title: "Application console",
        file: "user-guide/06-application-console.md",
        description: "Users, groups, roles, permissions, audit, logs, sessions",
      },
      {
        slug: "guide-settings",
        title: "Settings",
        file: "user-guide/07-settings.md",
        description: "Platform and application settings, tab by tab",
      },
      {
        slug: "guide-access-review",
        title: "Access review and operators",
        file: "user-guide/08-access-review-and-operators.md",
        description: "Privileged access review, platform operators",
      },
      {
        slug: "guide-configuration-as-code",
        title: "Configuration as code (console)",
        file: "user-guide/09-configuration-as-code.md",
        description: "Export, preview and apply files",
      },
      {
        slug: "guide-end-user-pages",
        title: "End-user pages",
        file: "user-guide/10-end-user-pages.md",
        description: "Hosted sign-in, sign-up, recovery, account security",
      },
      {
        slug: "guide-common-tasks",
        title: "Common tasks",
        file: "user-guide/11-common-tasks.md",
        description: "Step-by-step recipes and what is coming soon",
      },
      {
        slug: "guide-two-step-verification",
        title: "Two-step verification",
        file: "user-guide/12-two-step-verification.md",
        description: "Options, setup now or later, settings and locks, reset, extend, exempt",
      },
    ],
  },
  {
    id: "platform-fundamentals",
    label: "Platform — fundamentals",
    description: "Vision, architecture, data & security",
    pages: [
      { slug: "overview", title: "Overview", file: "01-overview.md", description: "Vision, goals, glossary" },
      { slug: "tech-stack", title: "Tech stack", file: "02-tech-stack.md" },
      { slug: "architecture", title: "Architecture", file: "03-architecture.md", description: "Components & flows" },
      { slug: "data-model", title: "Data model", file: "04-data-model.md", description: "ERD & tables" },
      { slug: "auth-standards", title: "Auth standards", file: "05-auth-standards.md", description: "OAuth, OIDC, MFA" },
      { slug: "database", title: "Database strategy", file: "06-database.md", description: "Platform & app schemas" },
      {
        slug: "database-schemas",
        title: "Schemas & tables",
        file: "17-database-schemas-and-tables.md",
        description: "Tables, FKs, connections",
      },
      { slug: "security", title: "Security", file: "07-security.md" },
    ],
  },
  {
    id: "platform-operations",
    label: "Platform — operations",
    description: "Install, govern, and run",
    pages: [
      { slug: "installation", title: "Installation", file: "09-installation.md", description: "Local setup" },
      {
        slug: "environment-setup",
        title: "Environment & operations",
        file: "16-environment-setup-and-operations.md",
        description: "Run stack, config, schedulers",
      },
      { slug: "roadmap", title: "Roadmap", file: "08-roadmap.md" },
      { slug: "enterprise", title: "Enterprise", file: "10-enterprise.md" },
      { slug: "settings-governance", title: "Settings governance", file: "10-settings-governance.md" },
      { slug: "admin-control", title: "Admin control", file: "11-admin-control.md" },
      {
        slug: "roles-and-access",
        title: "Roles and access",
        file: "25-roles-and-access.md",
        description: "Who sees and can do what, MFA, access review, RLS",
      },
      { slug: "application-rbac", title: "Application RBAC", file: "14-application-rbac-management.md" },
      {
        slug: "applications-oauth",
        title: "Applications & OAuth clients",
        file: "15-applications-and-oauth-clients.md",
        description: "Products, schemas, isolate",
      },
      {
        slug: "verification-otp",
        title: "Verification & OTP",
        file: "19-verification-and-otp.md",
        description: "Signup/login OTP, phone, SMS",
      },
    ],
  },
  {
    id: "deployment",
    label: "Deployment",
    description: "Ship SecureOne: images, env, Helm, enterprise rollout",
    pages: [
      {
        slug: "deployment-image-env-enterprise",
        title: "Deployment: image + env & enterprise",
        file: "24-deployment-image-env-and-enterprise.md",
        description: "Path A: Docker images + env only · Path B: source/Helm",
      },
      {
        slug: "configuration-as-code-and-upgrades",
        title: "Configuration as code and upgrades",
        file: "26-configuration-as-code-and-upgrades.md",
        description: "Tenant setup in files (export, preview, apply); upgrades and rollback",
      },
      {
        slug: "deploy-runbook",
        title: "Deploy runbook",
        file: "runbooks/deploy.md",
        description: "Prerequisites, images, Helm, post-deploy checks",
      },
    ],
  },
  {
    id: "api-reference",
    label: "API reference",
    description: "Every HTTP endpoint with curl, generated from the OpenAPI document",
    pages: [
      {
        slug: "api-reference",
        title: "API reference",
        file: "api-reference/README.md",
        description: "Every endpoint section by section, with curl",
      },
      {
        slug: "api-getting-started",
        title: "0. Getting started",
        file: "api-reference/00-getting-started.md",
        description: "Shell setup, credentials, admin session token, errors",
      },
      {
        slug: "api-oauth-oidc",
        title: "1. OAuth 2.1 and OpenID Connect",
        file: "api-reference/01-oauth-oidc.md",
        description: "Discovery, authorize, token, revoke, introspect, UserInfo",
      },
      {
        slug: "api-public-and-setup",
        title: "2. Public endpoints and setup",
        file: "api-reference/02-public-and-setup.md",
        description: "Server info, sign-in methods, first-run setup",
      },
      {
        slug: "api-application-sign-in",
        title: "3. Application sign-in and sign-up",
        file: "api-reference/03-application-sign-in.md",
        description: "Native UI: manifest, password, OTP, MFA, passkeys, federation",
      },
      {
        slug: "api-account",
        title: "4. Account self-service",
        file: "api-reference/04-account.md",
        description: "Password, email verification, profile, MFA, sessions, privacy",
      },
      {
        slug: "api-machine-and-scim",
        title: "5. Machine-to-machine and SCIM",
        file: "api-reference/05-machine-and-scim.md",
        description: "API keys and SCIM 2.0 provisioning",
      },
      {
        slug: "api-admin-sign-in",
        title: "6. Admin API: sign-in and context",
        file: "api-reference/06-admin-sign-in.md",
        description: "Console session token, context, operator profile",
      },
      {
        slug: "api-admin-tenants",
        title: "7. Admin API: tenants",
        file: "api-reference/07-admin-tenants.md",
        description: "Tenants, workspace, roles, console access",
      },
      {
        slug: "api-admin-users",
        title: "8. Admin API: users",
        file: "api-reference/08-admin-users.md",
        description: "Users, passwords, MFA reset, unlock, impersonation",
      },
      {
        slug: "api-admin-applications",
        title: "9. Admin API: applications and OAuth clients",
        file: "api-reference/09-admin-applications-and-clients.md",
        description: "Applications, OAuth clients, change approvals",
      },
      {
        slug: "api-admin-application-console",
        title: "10. Admin API: application console",
        file: "api-reference/10-admin-application-console.md",
        description: "An application's users, roles, permissions, settings, logs",
      },
      {
        slug: "api-admin-governance",
        title: "11. Admin API: security and governance",
        file: "api-reference/11-admin-security-and-governance.md",
        description: "Operators, access review, federation, service accounts, webhooks, audit",
      },
      {
        slug: "api-admin-settings",
        title: "12. Admin API: platform settings",
        file: "api-reference/12-admin-platform-settings.md",
        description: "Platform settings and configuration as code",
      },
      {
        slug: "api-data-models",
        title: "13. Data models",
        file: "api-reference/13-data-models.md",
        description: "Every request and response type",
      },
    ],
  },
  {
    id: "platform-reference",
    label: "Platform — reference",
    description: "APIs, ADRs, diagrams",
    pages: [
      {
        slug: "api-documentation",
        title: "API documentation",
        file: "12-api-documentation.md",
        description: "Reference, curl, OpenAPI and Swagger UI",
      },
      { slug: "decisions", title: "Architecture decisions", file: "decisions/README.md", description: "ADRs" },
      { slug: "diagrams", title: "Diagrams", file: "diagrams/README.md", description: "Mermaid + draw.io index" },
      {
        slug: "flyway-migrations",
        title: "Flyway migration files",
        file: "18-flyway-migration-files.md",
        description: "V1–V45 SQL reference",
      },
    ],
  },
  {
    id: "application-integration",
    label: "Application integration",
    description: "Developers wiring products into SecureOne",
    pages: [
      {
        slug: "application-integration",
        title: "Application integration guide",
        file: "23-application-integration-guide.md",
        description: "Demo Store example, APIs, hosted vs native UI",
      },
      {
        slug: "auth-ui-integration",
        title: "Auth UI integration",
        file: "13-auth-ui-integration.md",
        description: "Hosted vs native login",
      },
      {
        slug: "verification-otp",
        title: "Verification & OTP",
        file: "19-verification-and-otp.md",
        description: "Signup/login OTP, phone, SMS",
      },
    ],
  },
];

/** @deprecated Use CONFLUENCE_CATEGORIES — kept for flat page iteration. */
export const CONFLUENCE_SECTION = {
  id: "documentation",
  label: "Documentation",
  pages: CONFLUENCE_CATEGORIES.flatMap((c) => c.pages),
};

const pages = CONFLUENCE_SECTION.pages;

export function getAllConfluencePages(): ConfluencePage[] {
  return pages;
}

export function getConfluencePage(slug: string): ConfluencePage | undefined {
  return pages.find((p) => p.slug === slug);
}

export function getDefaultConfluenceSlug(): string {
  return "home";
}

/** Confluence slug that contains the inline draw.io embed for each source file. */
export const DRAWIO_DOCUMENT_SLUG: Record<string, string> = {
  "logical-architecture.drawio": "architecture",
  "auth-flow-authorization-code-pkce.drawio": "architecture",
  "auth-flow-client-credentials.drawio": "architecture",
  "auth-flow-refresh-rotation.drawio": "architecture",
  "auth-flow-hosted-vs-native.drawio": "architecture",
  "auth-flow-device-code.drawio": "architecture",
  "auth-flow-saml.drawio": "architecture",
  "deployment-mvp.drawio": "architecture",
  "deployment-production.drawio": "architecture",
  "data-layer-layering.drawio": "database",
  "tenant-isolation.drawio": "database",
  "enterprise-capability-map.drawio": "enterprise",
  "config-inheritance.drawio": "admin-control",
  "roles-permissions.drawio": "data-model",
};

export function drawioDocumentSlug(assetPath: string): string | undefined {
  const fileName = assetPath.split("/").pop() ?? assetPath;
  return DRAWIO_DOCUMENT_SLUG[fileName];
}

/** Map `01-overview.md` / `decisions/README.md` → slug for link rewriting */
export function buildFileToSlugMap(): Map<string, string> {
  const map = new Map<string, string>();
  for (const p of pages) {
    map.set(p.file, p.slug);
    map.set(`./${p.file}`, p.slug);
    map.set(`../${p.file}`, p.slug);
  }
  // Bare file names last and never over an exact path: several folders have a README.md.
  for (const p of pages) {
    const base = p.file.split("/").pop();
    if (base && !map.has(base)) map.set(base, p.slug);
  }
  return map;
}

export function confluenceCatalogJson() {
  return {
    name: "SecureOne Confluence",
    defaultSlug: getDefaultConfluenceSlug(),
    uiPath: "/confluence",
    apiPath: "/api/confluence",
    categories: CONFLUENCE_CATEGORIES.map((category) => ({
      id: category.id,
      label: category.label,
      description: category.description,
      pages: category.pages.map((page) => ({
        slug: page.slug,
        title: page.title,
        file: page.file,
        description: page.description,
        uiPath: `/confluence/${page.slug}`,
      })),
    })),
    /** Flat list for backward compatibility */
    section: CONFLUENCE_SECTION,
  };
}
