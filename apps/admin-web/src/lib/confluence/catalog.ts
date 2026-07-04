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
    id: "platform-reference",
    label: "Platform — reference",
    description: "APIs, ADRs, diagrams",
    pages: [
      {
        slug: "api-documentation",
        title: "API documentation",
        file: "12-api-documentation.md",
        description: "OpenAPI & Swagger",
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
      {
        slug: "application-integration",
        title: "Application integration guide",
        file: "applications/README.md",
        description: "Setup, APIs, samples",
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
    const base = p.file.split("/").pop();
    if (base) map.set(base, p.slug);
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
