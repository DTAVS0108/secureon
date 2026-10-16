/**
 * Domain types for the admin console. These mirror the platform data model
 * (see docs/04-data-model.md) and are consumed by the mock data layer in
 * data.ts. When the backend admin API lands, the data layer is swapped for
 * real fetch calls while these types stay stable.
 */

export type Status = "active" | "suspended" | "invited" | "disabled";

export type Tenant = {
  id: string;
  name: string;
  slug: string;
  status: Status;
  plan: "free" | "team" | "enterprise";
  userCount: number;
  appCount: number;
  createdAt: string;
  /** Reserved platform tenant: no delete, slug change or suspend (server enforces it too). */
  system?: boolean;
};

export type AppType = "web" | "spa" | "native" | "m2m";

export type OAuthEndpoints = {
  issuer: string;
  authorizationEndpoint: string;
  tokenEndpoint: string;
  jwksUri: string;
};

export type ApplicationProduct = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description?: string | null;
  status: Status;
  schemaName?: string | null;
  oauthClientCount: number;
  createdAt: string;
  updatedAt: string;
};

/** OAuth protocol client registered under an application product. */
export type OAuthClient = {
  id: string;
  applicationId: string;
  tenantId: string;
  applicationName: string;
  clientId: string;
  type: AppType;
  status: Status;
  grantTypes: string[];
  scopes: string[];
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  confidential: boolean;
  pkceRequired: boolean;
  tokenEndpointAuthMethod: string;
  /** Token "sub": "user_id" (account UUID, stable) or "legacy" ("tenant:email"). */
  subjectType?: "user_id" | "legacy";
  clientSecretConfigured: boolean;
  createdAt: string;
  updatedAt: string;
  oAuthEndpoints: OAuthEndpoints;
};

/** @deprecated Use ApplicationProduct for product views or OAuthClient for OAuth registry views. */
export type Application = ApplicationProduct;

export type MfaFactorType = "passkey" | "totp" | "sms" | "email" | "push";

export type MfaFactor = {
  id: string;
  type: MfaFactorType;
  label: string;
  verified: boolean;
  addedAt: string;
};

export type User = {
  id: string;
  tenantId: string;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  status: Status;
  emailVerified: boolean;
  /** E.164, when the user registered one. */
  phone?: string | null;
  phoneVerified?: boolean;
  hasPassword?: boolean;
  locked?: boolean;
  failedLoginCount?: number;
  roleIds: string[];
  roleNames: string[];
  mfaFactors: MfaFactor[];
  /** Per-user allow-list for app-enabled sign-in methods (app-scoped fetch only). */
  allowedAuthMethods?: Record<string, boolean>;
  lastLoginAt: string | null;
  createdAt: string;
};

export type Permission = {
  id: string;
  applicationId?: string;
  key: string;
  resource: string;
  action: string;
  description: string;
  roleCount?: number;
};

export type PermissionDetail = Permission & {
  roles: { id: string; name: string }[];
};

export type RoleLabel = "SYSTEM" | "BUILT_IN" | "COMPOSITE" | "CUSTOM";

export type Role = {
  id: string;
  tenantId: string;
  applicationId?: string;
  name: string;
  description: string;
  isComposite: boolean;
  isSystem: boolean;
  isDefault: boolean;
  label: RoleLabel;
  permissionIds: string[];
  childRoleIds: string[];
  userCount: number;
  permissionCount?: number;
  childRoleCount?: number;
};

export type RoleDetail = Role & {
  permissions: Permission[];
  childRoles: { id: string; name: string }[];
};

export type RoleAssignedUser = {
  id: string;
  email: string;
  username: string;
  displayName: string;
  status: string;
  emailVerified: boolean;
  grantedAt: string | null;
};

export type RbacGroup = {
  id: string;
  tenantId: string;
  applicationId: string;
  name: string;
  description: string;
  createdAt: string;
  roleCount: number;
  memberCount: number;
  roleIds: string[];
  memberUserIds: string[];
};

export type RbacGroupDetail = RbacGroup & {
  roles: { id: string; name: string }[];
  members: {
    id: string;
    email: string;
    username: string;
    displayName: string;
    status: string;
    emailVerified: boolean;
    addedAt: string | null;
  }[];
};

export type AuditEvent = {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  target: string;
  ip: string;
  result: "success" | "failure";
};

export type LoginEvent = {
  id: string;
  userId: string;
  userEmail: string;
  timestamp: string;
  ip: string;
  location: string;
  device: string;
  method: "password" | "passkey" | "totp" | "social";
  result: "success" | "failure" | "mfa_required";
  sessionId?: string | null;
};

export type ApplicationLogEntry = {
  id: string;
  timestamp: string;
  level: "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR";
  logger: string;
  message: string;
  sessionId: string | null;
  requestId: string | null;
  principal: string | null;
  tenantId: string | null;
  applicationId: string | null;
  ip: string | null;
  userAgent: string | null;
  metadata: Record<string, unknown>;
};

export type AuthMethod = {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  category: "primary" | "mfa" | "federation";
  implemented?: boolean;
};

export type FeatureFlag = {
  key: string;
  name: string;
  description: string;
  enabled: boolean;
  rollout: number;
  /** Grouping for admin UI: oauth, identity, notifications, provisioning */
  category?: string;
  /** Platform master switch; when false, application cannot enable this flag. */
  platformEnabled?: boolean;
};

export type PasswordRequirement = {
  key: string;
  label: string;
};

export type PasswordPolicy = {
  minLength: number;
  requireUppercase: boolean;
  requireNumber: boolean;
  requireSymbol: boolean;
  expiryDays: number;
  historyCount: number;
  hashAlgorithm: string;
  breachCheck?: boolean;
  /** Wrong passwords or codes in a row before the account is locked. */
  maxFailedAttempts?: number;
  lockoutDurationMinutes?: number;
  requirements?: PasswordRequirement[];
  inheritsPlatformDefaults?: boolean;
};
