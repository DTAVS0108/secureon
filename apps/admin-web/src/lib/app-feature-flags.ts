/**
 * Console controls: feature flags that show or hide parts of the admin console. They are decided in
 * Platform settings → Feature flags → Console controls for every application (applications cannot change
 * them) and arrive with the admin context, so no per-application settings call is needed.
 */
export const APP_FLAG = {
  integrationChecklist: "integration_checklist",
  apiReference: "api_reference",
  apiHealthCheck: "api_health_check",
  emailTemplates: "email_templates",
  emailTestConsole: "notification_email_test_ui",
  federationAddProvider: "federation_add_provider",
  auditLog: "audit_log",
  sessions: "sessions",
} as const;

export type AppFlagKey = (typeof APP_FLAG)[keyof typeof APP_FLAG];

/** Same defaults as the server catalog (FeatureFlagDefaults); used when a value is missing. */
const DEFAULTS: Record<AppFlagKey, boolean> = {
  integration_checklist: false,
  api_reference: false,
  api_health_check: false,
  email_templates: true,
  notification_email_test_ui: true,
  federation_add_provider: true,
  audit_log: true,
  sessions: true,
};

export type AppFlags = Record<string, boolean>;

export function appFlagOn(flags: AppFlags | null | undefined, key: AppFlagKey): boolean {
  return flags?.[key] ?? DEFAULTS[key];
}
