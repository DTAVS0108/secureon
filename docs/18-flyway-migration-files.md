# 18 — Flyway Migration Files

Complete SQL reference for all database migrations. Source files live under `apps/auth-server/src/main/resources/db/migration/`.

Related: [17 — Database schemas & tables](17-database-schemas-and-tables.md) · [06 — Database strategy](06-database.md) · [16 — Environment & operations](16-environment-setup-and-operations.md)

---

## How migrations run

| Path | Mechanism | History |
|------|-----------|---------|
| `postgresql/V*.sql` | Flyway on auth-server boot | `public.flyway_schema_history` |
| `postgresql/R__*.sql` | Flyway repeatable (re-run when checksum changes) | same |
| `application-schema/V1__app_core.sql` | `ApplicationSchemaProvisioner` at app create/isolate | `platform.application_schema.flyway_version` |

**Flyway config** (`application.yml`): `spring.flyway.locations: classpath:db/migration/postgresql`

**Manual run:** `cd apps/auth-server && ./gradlew flywayMigrate`

**Catalog repair:** `.\scripts\reseed-catalog.ps1` applies `R__z_repair_catalog.sql`

---

## Migration index

| Version | File | Summary |
|---------|------|---------|
| V1 | `V1__core_schema.sql` | core schema |
| V2 | `V2__seed_dev_data.sql` | seed dev data |
| V3 | `V3__platform_settings_and_app_config.sql` | platform settings and app config |
| V4 | `V4__seed_roles_and_admin_assignments.sql` | seed roles and admin assignments |
| V5 | `V5__email_tokens_and_user_notifications.sql` | email tokens and user notifications |
| V6 | `V6__auth_settings_mfa_and_magic_link.sql` | auth settings mfa and magic link |
| V7 | `V7__dev_password_credentials.sql` | dev password credentials |
| V8 | `V8__application_scoped_users_and_settings.sql` | application scoped users and settings |
| V9 | `V9__application_scope_audit_sessions_access.sql` | application scope audit sessions access |
| V10 | `V10__app_settings_exposure.sql` | app settings exposure |
| V11 | `V11__rbac_permissions_seed.sql` | rbac permissions seed |
| V12 | `V12__default_permissions_all_apps.sql` | default permissions all apps |
| V13 | `V13__user_directory_import_export.sql` | user directory import export |
| V14 | `V14__public_application_manifest.sql` | public application manifest |
| V15 | `V15__token_policy.sql` | token policy |
| V16 | `V16__signup_default_member_role.sql` | signup default member role |
| V17 | `V17__external_test_application.sql` | external test application |
| V18 | `V18__application_logs.sql` | application logs |
| V19 | `V19__smtp_email_templates.sql` | smtp email templates |
| V20 | `V20__user_credential_one_current.sql` | user credential one current |
| V21 | `V21__restore_dev_tenant_admin.sql` | restore dev tenant admin |
| V22 | `V22__dev_reset_acme_operator_password.sql` | dev reset acme operator password |
| V23 | `V23__admin_console_access.sql` | admin console access |
| V24 | `V24__dedupe_admin_console_access.sql` | dedupe admin console access |
| V25 | `V25__remove_dev_seed_data.sql` | remove dev seed data |
| V26 | `V26__purge_dev_sample_rows.sql` | purge dev sample rows |
| V27 | `V27__finalize_dev_data_removal.sql` | finalize dev data removal |
| V28 | `V28__seed_default_roles_all_apps.sql` | seed default roles all apps |
| V29 | `V29__tenant_user_roster.sql` | tenant user roster |
| V30 | `V30__repair_invited_application_members.sql` | repair invited application members |
| V31 | `V31__tenant_roster_metadata.sql` | tenant roster metadata |
| V32 | `V32__admin_console_feature_overrides.sql` | admin console feature overrides |
| V33 | `V33__cleanup_orphan_console_roster_rows.sql` | cleanup orphan console roster rows |
| V34 | `V34__tenant_rbac.sql` | tenant rbac |
| V35 | `V35__seed_tenant_rbac_existing.sql` | seed tenant rbac existing |
| V36 | `V36__tenant_console_role_features.sql` | tenant console role features |
| V37 | `V37__mark_tenant_rbac_system_roles.sql` | mark tenant rbac system roles |
| V38 | `V38__platform_notifications_independent.sql` | platform notifications independent |
| V39 | `V39__user_credential_expires_at.sql` | user credential expires at |
| V40 | `V40__password_expiry_notifications.sql` | password expiry notifications |
| V41 | `V41__trim_feature_flag_catalog.sql` | trim feature flag catalog |
| V42 | `V42__application_rbac_groups.sql` | application rbac groups |
| V43 | `V43__tenant_console_groups.sql` | tenant console groups |
| V44 | `V44__reseed_platform_settings.sql` | reseed platform settings |
| V45 | `V45__platform_schema_oauth_client.sql` | platform schema oauth client |
| V46 | `V46__otp_verification_and_phone.sql` | otp verification and phone |
| V47 | `V47__expose_sms_to_applications.sql` | expose sms to applications |
| V48 | `V48__recovery_otp_and_verification_policy.sql` | recovery otp and verification policy |
| V49 | `V49__oauth2_authorization_jdbc.sql` | oauth2 authorization jdbc |
| V50 | `V50__signing_keys.sql` | signing keys |
| V52 | `V52__mfa_factor_extended.sql` | mfa factor extended |
| V53 | `V53__mfa_policy.sql` | mfa policy |
| V54 | `V54__tenant_rls_policies.sql` | tenant rls policies |
| V55 | `V55__identity_federation.sql` | identity federation |
| V56 | `V56__scim_groups_service_accounts.sql` | scim groups service accounts |
| V57 | `V57__webhooks_trusted_device_iga.sql` | webhooks trusted device iga |
| V58 | `V58__access_request_application_id.sql` | access request application id |
| V59 | `V59__access_request_granted_role_name.sql` | access request granted role name |
| V60 | `V60__terrafarm_oauth_redirect.sql` | terrafarm oauth redirect |
| V61 | `V61__rls_fail_closed_option.sql` | rls fail closed option |
| V62 | `V62__named_platform_operators.sql` | named platform operators |
| V63 | `V63__rls_application_role.sql` | rls application role |
| V64 | `V64__rls_functions_schema_qualified.sql` | rls functions schema qualified |
| V65 | `V65__remove_stale_platform_rbac_copies.sql` | remove stale platform rbac copies |
| V66 | `V66__application_logs_single_table.sql` | application logs single table |
| V67 | `V67__oauth_client_subject_type.sql` | oauth client subject type |
| R | `R__z_repair_catalog.sql` | idempotent catalog repair (repeatable) |

---

## Full SQL

### Application schema template

```sql
-- Per-application schema DDL (executed inside each app schema, e.g. demostore).
-- Cross-schema FKs reference platform.* for tenant, application, and user identity.

CREATE TABLE IF NOT EXISTS permission (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    key            VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    UNIQUE (application_id, key)
);

CREATE TABLE IF NOT EXISTS role (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    is_default     BOOLEAN NOT NULL DEFAULT false,
    is_system      BOOLEAN NOT NULL DEFAULT false,
    is_composite   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (application_id, name)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS role_composite (
    parent_role_id UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    child_role_id  UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id),
    CHECK (parent_role_id <> child_role_id)
);

CREATE TABLE IF NOT EXISTS user_role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID,
    role_id    UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    granted_by UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS user_application (
    user_id        UUID NOT NULL,
    application_id UUID NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, application_id)
);

CREATE TABLE IF NOT EXISTS application_setting (
    application_id UUID NOT NULL,
    setting_key    VARCHAR(150) NOT NULL,
    value          JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, setting_key)
);

CREATE TABLE IF NOT EXISTS rbac_group (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (application_id, name)
);

CREATE TABLE IF NOT EXISTS rbac_group_role (
    group_id UUID NOT NULL REFERENCES rbac_group (id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);

CREATE TABLE IF NOT EXISTS rbac_group_member (
    group_id UUID NOT NULL REFERENCES rbac_group (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS application_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    level           VARCHAR(16) NOT NULL,
    logger          VARCHAR(256) NOT NULL,
    message         TEXT NOT NULL,
    session_id      VARCHAR(128),
    request_id      VARCHAR(64),
    principal       VARCHAR(320),
    tenant_id       UUID,
    application_id  UUID,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_application_log_created_at ON application_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_application_log_session_id ON application_log (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_application_log_request_id ON application_log (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_application_log_level ON application_log (level);
CREATE INDEX IF NOT EXISTS idx_application_log_app_created ON application_log (application_id, created_at DESC)
    WHERE application_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_role_application ON role (application_id);
CREATE INDEX IF NOT EXISTS idx_permission_application ON permission (application_id);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_rbac_group_application ON rbac_group (application_id);

```

### `V1__core_schema.sql`

```sql
-- SecureOne core schema (MVP) â€” PostgreSQL
-- Multi-tenant IAM: tenants, applications, users, RBAC (with composite roles), audit.
-- See docs/04-data-model.md. UUID v7 generated in the app; gen_random_uuid() used as a DB default fallback.

-- ----------------------------------------------------------------------------
-- Domain 1 â€” Tenancy & Applications
-- ----------------------------------------------------------------------------
CREATE TABLE tenant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    settings    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE application (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, slug)
);
CREATE INDEX idx_application_tenant ON application(tenant_id);

-- ----------------------------------------------------------------------------
-- Domain 2 â€” Identity
-- ----------------------------------------------------------------------------
CREATE TABLE user_account (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email              VARCHAR(320) NOT NULL,
    email_verified     BOOLEAN      NOT NULL DEFAULT false,
    username           VARCHAR(150),
    phone              VARCHAR(32),
    phone_verified     BOOLEAN      NOT NULL DEFAULT false,
    display_name       VARCHAR(255),
    status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    type               VARCHAR(32)  NOT NULL DEFAULT 'USER',
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    attributes         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);
CREATE INDEX idx_user_tenant_status ON user_account(tenant_id, status);

CREATE TABLE user_credential (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    algorithm     VARCHAR(64)  NOT NULL DEFAULT 'argon2id',
    params        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_current    BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_credential_user ON user_credential(user_id);

-- ----------------------------------------------------------------------------
-- Domain 3 â€” Authorization (RBAC + composite roles)
-- ----------------------------------------------------------------------------
CREATE TABLE permission (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    key            VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    UNIQUE (application_id, key)
);

CREATE TABLE role (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    is_default     BOOLEAN NOT NULL DEFAULT false,
    is_system      BOOLEAN NOT NULL DEFAULT false,
    is_composite   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (application_id, name)
);
CREATE INDEX idx_role_tenant ON role(tenant_id);

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Composite / hierarchical roles (a parent role includes child roles).
CREATE TABLE role_composite (
    parent_role_id UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    child_role_id  UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id),
    CHECK (parent_role_id <> child_role_id)
);
CREATE INDEX idx_role_composite_child ON role_composite(child_role_id);

CREATE TABLE user_role (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID REFERENCES user_account(id) ON DELETE CASCADE,
    role_id            UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    granted_by         UUID,
    granted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ
);
CREATE INDEX idx_user_role_user ON user_role(user_id);
CREATE INDEX idx_user_role_role ON user_role(role_id);

-- ----------------------------------------------------------------------------
-- Domain 7 â€” Audit & history (append-only)
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID,
    actor_type  VARCHAR(32) NOT NULL,
    actor_id    UUID,
    action      VARCHAR(150) NOT NULL,
    target_type VARCHAR(100),
    target_id   UUID,
    ip          VARCHAR(64),
    user_agent  VARCHAR(512),
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant_created ON audit_log(tenant_id, created_at);

CREATE TABLE login_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID,
    user_id    UUID,
    result     VARCHAR(32) NOT NULL,
    ip         VARCHAR(64),
    device     VARCHAR(255),
    geo        JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_history_tenant_created ON login_history(tenant_id, created_at);

```

### `V2__seed_dev_data.sql`

```sql
-- Dev seed data for admin console smoke tests (idempotent).

INSERT INTO tenant (id, slug, name, status, settings, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'acme', 'Acme Corp', 'ACTIVE',
     '{"plan":"enterprise"}'::jsonb, now(), now()),
    ('11111111-1111-1111-1111-111111111102', 'globex', 'Globex', 'ACTIVE',
     '{"plan":"team"}'::jsonb, now(), now())
ON CONFLICT (slug) DO NOTHING;

INSERT INTO application (id, tenant_id, name, slug, status, created_at, updated_at)
VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101',
     'Acme Web Portal', 'acme-web', 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO user_account (id, tenant_id, email, email_verified, username, display_name, status, type, created_at, updated_at)
VALUES
    ('33333333-3333-3333-3333-333333333301', '11111111-1111-1111-1111-111111111101',
     'sarah.chen@acme.com', true, 'schen', 'Sarah Chen', 'ACTIVE', 'USER', now(), now()),
    ('33333333-3333-3333-3333-333333333302', '11111111-1111-1111-1111-111111111102',
     'yuki.tanaka@globex.com', true, 'ytanaka', 'Yuki Tanaka', 'ACTIVE', 'USER', now(), now())
ON CONFLICT (tenant_id, email) DO NOTHING;

```

### `V3__platform_settings_and_app_config.sql`

```sql
-- Platform settings, application OAuth config, and dev seed for audit/login history.

ALTER TABLE application
    ADD COLUMN IF NOT EXISTS config JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS platform_setting (
    key         VARCHAR(150) PRIMARY KEY,
    value       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Default notification + email settings (admin can change via API).
INSERT INTO platform_setting (key, value)
VALUES
    ('notifications', '{
      "emailEnabled": true,
      "userEmailEnabled": true,
      "pushEnabled": false,
      "auditAlertsEnabled": true,
      "securityAlertsEnabled": true,
      "adminRecipients": ["admin@acme.com"]
    }'::jsonb),
    ('email', '{
      "fromName": "SecureOne",
      "fromAddress": "noreply@secureone.local",
      "replyTo": "support@secureone.local"
    }'::jsonb)
ON CONFLICT (key) DO NOTHING;

UPDATE application
SET config = '{
  "type": "web",
  "clientId": "acme-web",
  "grantTypes": ["authorization_code", "refresh_token"],
  "scopes": ["openid", "profile", "email"],
  "redirectUris": ["https://app.acme.com/callback"]
}'::jsonb
WHERE id = '22222222-2222-2222-2222-222222222201';

INSERT INTO audit_log (id, tenant_id, actor_type, actor_id, action, target_type, target_id, ip, metadata, created_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111101', 'USER', '33333333-3333-3333-3333-333333333301',
     'user.login.success', 'user_account', '33333333-3333-3333-3333-333333333301', '203.0.113.10',
     '{"result":"success","actorEmail":"sarah.chen@acme.com","targetLabel":"sarah.chen@acme.com"}'::jsonb, now() - interval '2 hours'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '11111111-1111-1111-1111-111111111101', 'ADMIN', NULL,
     'tenant.updated', 'tenant', '11111111-1111-1111-1111-111111111101', '203.0.113.10',
     '{"result":"success","actorEmail":"admin","targetLabel":"Acme Corp"}'::jsonb, now() - interval '1 hour')
ON CONFLICT DO NOTHING;

INSERT INTO login_history (id, tenant_id, user_id, result, ip, device, geo, created_at)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301',
     'SUCCESS', '203.0.113.10', 'Chrome Â· macOS', '{"location":"San Francisco, US","method":"passkey"}'::jsonb, now() - interval '3 hours'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '11111111-1111-1111-1111-111111111102', '33333333-3333-3333-3333-333333333302',
     'SUCCESS', '198.51.100.5', 'Edge Â· Windows', '{"location":"Tokyo, JP","method":"passkey"}'::jsonb, now() - interval '5 hours')
ON CONFLICT DO NOTHING;

```

### `V4__seed_roles_and_admin_assignments.sql`

```sql
-- Dev roles and admin assignments for notification recipient picker.

INSERT INTO role (id, tenant_id, application_id, name, description, is_composite)
VALUES
    ('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222201', 'Super Admin',
     'Full platform control', true),
    ('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222201', 'Tenant Admin',
     'Manage users, roles, and applications', false),
    ('44444444-4444-4444-4444-444444444403', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222201', 'Member',
     'Standard end-user access', false)
ON CONFLICT DO NOTHING;

INSERT INTO role_composite (parent_role_id, child_role_id)
VALUES ('44444444-4444-4444-4444-444444444401', '44444444-4444-4444-4444-444444444402')
ON CONFLICT DO NOTHING;

INSERT INTO user_role (id, user_id, role_id, granted_at)
VALUES
    ('55555555-5555-5555-5555-555555555501', '33333333-3333-3333-3333-333333333301',
     '44444444-4444-4444-4444-444444444402', now())
ON CONFLICT (id) DO NOTHING;

UPDATE platform_setting
SET value = jsonb_set(
    value,
    '{adminRecipients}',
    '["sarah.chen@acme.com"]'::jsonb,
    true)
WHERE key = 'notifications';

```

### `V5__email_tokens_and_user_notifications.sql`

```sql
-- Transactional email tokens (verify email, reset password).

CREATE TABLE email_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    type        VARCHAR(32) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_token_user_type ON email_token(user_id, type);
CREATE INDEX idx_email_token_hash ON email_token(token_hash);

-- Enable user-facing transactional email by default (admin alerts remain separate).
UPDATE platform_setting
SET value = value || '{"userEmailEnabled": true}'::jsonb
WHERE key = 'notifications'
  AND NOT (value ? 'userEmailEnabled');

```

### `V6__auth_settings_mfa_and_magic_link.sql`

```sql
-- MFA factors + default auth settings keys.

CREATE TABLE mfa_factor (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    type        VARCHAR(32) NOT NULL,
    label       VARCHAR(255),
    secret_encrypted BYTEA,
    verified    BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_factor_user ON mfa_factor(user_id);

INSERT INTO platform_setting (key, value)
VALUES
    ('auth_methods', '[
      {"id":"m_password","name":"Password","description":"Username + password","enabled":true,"category":"primary","implemented":true},
      {"id":"m_passkey","name":"Passkeys (WebAuthn)","description":"Phishing-resistant sign-in","enabled":true,"category":"primary","implemented":false},
      {"id":"m_magic","name":"Magic Link","description":"Email passwordless login","enabled":true,"category":"primary","implemented":true},
      {"id":"m_totp","name":"TOTP Authenticator","description":"Authenticator app codes","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_sms","name":"SMS OTP","description":"SMS one-time codes","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_email_otp","name":"Email OTP","description":"Email one-time codes at login","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_push","name":"Push Notification","description":"Approve on device","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_google","name":"Google","description":"Google OIDC","enabled":true,"category":"federation","implemented":false},
      {"id":"m_github","name":"GitHub","description":"GitHub OAuth","enabled":true,"category":"federation","implemented":false},
      {"id":"m_saml","name":"SAML 2.0","description":"Enterprise SSO","enabled":false,"category":"federation","implemented":false},
      {"id":"m_oidc","name":"External OIDC","description":"OIDC federation","enabled":false,"category":"federation","implemented":false}
    ]'::jsonb),
    ('password_policy', '{
      "minLength": 12,
      "requireUppercase": true,
      "requireNumber": true,
      "requireSymbol": true,
      "expiryDays": 0,
      "historyCount": 5,
      "hashAlgorithm": "bcrypt"
    }'::jsonb),
    ('feature_flags', '[
      {"key":"adaptive_mfa","name":"Adaptive MFA","description":"Risk-based step-up","enabled":true,"rollout":100},
      {"key":"device_trust","name":"Trusted Devices","description":"Remember devices","enabled":true,"rollout":100},
      {"key":"scim_provisioning","name":"SCIM Provisioning","description":"SCIM 2.0","enabled":false,"rollout":25},
      {"key":"dpop","name":"DPoP Tokens","description":"Sender-constrained tokens","enabled":false,"rollout":10},
      {"key":"self_service_recovery","name":"Self-service Recovery","description":"Password reset & magic link","enabled":true,"rollout":100},
      {"key":"self_registration","name":"Self Registration","description":"Public signup","enabled":false,"rollout":0},
      {"key":"social_login","name":"Social Login","description":"Google/GitHub/OIDC","enabled":true,"rollout":100},
      {"key":"saml","name":"SAML SSO","description":"SAML 2.0","enabled":false,"rollout":0},
      {"key":"ldap","name":"LDAP / AD","description":"Directory login","enabled":false,"rollout":0},
      {"key":"passwordless","name":"Passwordless Primary","description":"Passkey-first login","enabled":true,"rollout":100}
    ]'::jsonb)
ON CONFLICT (key) DO NOTHING;

```

### `V7__dev_password_credentials.sql`

```sql
-- Dev-only: password for seeded users (SecureOne123!) â€” use forgot-password in prod-like envs.
INSERT INTO user_credential (user_id, password_hash, algorithm, is_current)
SELECT u.id, '$2a$10$l4rw.ZW0bub9AMUDZp5BoOeblXp6zfo7fohXX2Ws5/jLeIlj1hmwS', 'bcrypt', true
FROM user_account u
WHERE u.email IN ('sarah.chen@acme.com', 'yuki.tanaka@globex.com')
  AND NOT EXISTS (
      SELECT 1 FROM user_credential c WHERE c.user_id = u.id AND c.is_current = true
  );

```

### `V8__application_scoped_users_and_settings.sql`

```sql
-- Per-application user membership and settings overrides (fall back to platform defaults).

CREATE TABLE user_application (
    user_id        UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, application_id)
);
CREATE INDEX idx_user_application_app ON user_application(application_id);

CREATE TABLE application_setting (
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    setting_key    VARCHAR(150) NOT NULL,
    value          JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, setting_key)
);

-- Link dev seed user to Acme Web Portal client.
INSERT INTO user_application (user_id, application_id)
SELECT '33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201'
WHERE EXISTS (SELECT 1 FROM user_account WHERE id = '33333333-3333-3333-3333-333333333301')
  AND EXISTS (SELECT 1 FROM application WHERE id = '22222222-2222-2222-2222-222222222201')
ON CONFLICT DO NOTHING;

```

### `V9__application_scope_audit_sessions_access.sql`

```sql
-- Scope audit and login history to applications; index for app-scoped admin console.

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES application(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_audit_application_created ON audit_log(application_id, created_at DESC);

ALTER TABLE login_history
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES application(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_login_history_application_created ON login_history(application_id, created_at DESC);

UPDATE audit_log
SET application_id = '22222222-2222-2222-2222-222222222201'
WHERE tenant_id = '11111111-1111-1111-1111-111111111101' AND application_id IS NULL;

UPDATE login_history
SET application_id = '22222222-2222-2222-2222-222222222201'
WHERE tenant_id = '11111111-1111-1111-1111-111111111101' AND application_id IS NULL;

```

### `V10__app_settings_exposure.sql`

```sql
-- Which platform setting sections applications may customize.

INSERT INTO platform_setting (key, value)
VALUES (
    'app_settings_exposure',
    '{
      "notifications": true,
      "email": true,
      "auth-methods": true,
      "password-policy": true,
      "feature-flags": true,
      "appearance": false
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

```

### `V11__rbac_permissions_seed.sql`

```sql
-- Enterprise RBAC: permission catalog, role labels, and default assignments.

UPDATE role SET is_system = true, is_default = false
WHERE id = '44444444-4444-4444-4444-444444444401';

UPDATE role SET is_system = true, is_default = true
WHERE id IN (
    '44444444-4444-4444-4444-444444444402',
    '44444444-4444-4444-4444-444444444403'
);

INSERT INTO permission (id, application_id, key, description)
VALUES
    ('66666666-6666-6666-6666-666666666601', '22222222-2222-2222-2222-222222222201', 'user:read', 'View users and profiles'),
    ('66666666-6666-6666-6666-666666666602', '22222222-2222-2222-2222-222222222201', 'user:write', 'Create and edit users'),
    ('66666666-6666-6666-6666-666666666603', '22222222-2222-2222-2222-222222222201', 'user:delete', 'Delete users'),
    ('66666666-6666-6666-6666-666666666604', '22222222-2222-2222-2222-222222222201', 'role:read', 'View roles and permissions'),
    ('66666666-6666-6666-6666-666666666605', '22222222-2222-2222-2222-222222222201', 'role:write', 'Manage roles and assignments'),
    ('66666666-6666-6666-6666-666666666606', '22222222-2222-2222-2222-222222222201', 'app:read', 'View applications'),
    ('66666666-6666-6666-6666-666666666607', '22222222-2222-2222-2222-222222222201', 'app:write', 'Manage OAuth clients'),
    ('66666666-6666-6666-6666-666666666608', '22222222-2222-2222-2222-222222222201', 'audit:read', 'View audit logs'),
    ('66666666-6666-6666-6666-666666666609', '22222222-2222-2222-2222-222222222201', 'settings:write', 'Change application settings'),
    ('66666666-6666-6666-6666-666666666610', '22222222-2222-2222-2222-222222222201', 'session:read', 'View active sessions')
ON CONFLICT (application_id, key) DO NOTHING;

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
VALUES (
    '44444444-4444-4444-4444-444444444404',
    '11111111-1111-1111-1111-111111111101',
    '22222222-2222-2222-2222-222222222201',
    'Security Auditor',
    'Read-only access to audit and identity data',
    true,
    false,
    false
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.application_id = '22222222-2222-2222-2222-222222222201'
  AND p.application_id = '22222222-2222-2222-2222-222222222201'
  AND (
    (r.name = 'Tenant Admin' AND p.key IN (
        'user:read', 'user:write', 'user:delete', 'role:read', 'role:write',
        'app:read', 'app:write', 'settings:write', 'audit:read', 'session:read'))
    OR (r.name = 'Member' AND p.key = 'user:read')
    OR (r.name = 'Security Auditor' AND p.key IN ('audit:read', 'user:read', 'role:read', 'session:read'))
  )
ON CONFLICT DO NOTHING;

```

### `V12__default_permissions_all_apps.sql`

```sql
-- Seed default permission catalog for every application (idempotent).

INSERT INTO permission (id, application_id, key, description)
SELECT gen_random_uuid(), a.id, d.key, d.description
FROM application a
CROSS JOIN (
    VALUES
        ('user:read', 'View users and profiles'),
        ('user:write', 'Create and edit users'),
        ('user:delete', 'Delete users'),
        ('role:read', 'View roles and permissions'),
        ('role:write', 'Manage roles and assignments'),
        ('app:read', 'View applications'),
        ('app:write', 'Manage OAuth clients'),
        ('audit:read', 'View audit logs'),
        ('settings:write', 'Change application settings'),
        ('session:read', 'View active sessions')
) AS d(key, description)
WHERE NOT EXISTS (
    SELECT 1 FROM permission p
    WHERE p.application_id = a.id AND p.key = d.key
);

```

### `V13__user_directory_import_export.sql`

```sql
-- User directory import/export: platform defaults and app settings exposure.

INSERT INTO platform_setting (key, value)
VALUES (
    'user_directory',
    '{
      "importEnabled": false,
      "exportEnabled": false,
      "sources": {
        "csv": { "enabled": true },
        "excel": { "enabled": true },
        "ldap": { "enabled": false }
      },
      "ldap": {
        "host": "",
        "port": 389,
        "baseDn": "",
        "bindDn": "",
        "bindPassword": "",
        "userFilter": "(mail={0})",
        "useTls": true
      }
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

UPDATE platform_setting
SET value = value || '{"user-directory": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'user-directory');

```

### `V14__public_application_manifest.sql`

```sql
-- Public (unauthenticated) application manifest: platform defaults and settings tab exposure.

INSERT INTO platform_setting (key, value)
VALUES (
    'public_manifest_defaults',
    '{
      "enabled": false,
      "sections": {
        "application": true,
        "authMethods": true,
        "featureFlags": true,
        "passwordPolicy": true,
        "appearance": false
      },
      "authMethodsOnlyEnabled": true
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

UPDATE platform_setting
SET value = value || '{"public-manifest": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'public-manifest');

```

### `V15__token_policy.sql`

```sql
-- OAuth token policy (platform defaults + app exposure).

INSERT INTO platform_setting (key, value)
VALUES (
    'token_policy',
    '{
      "accessTokenTtlSeconds": 3600,
      "refreshTokenTtlSeconds": 604800,
      "authorizationCodeTtlSeconds": 300,
      "idTokenTtlSeconds": 3600,
      "clientCredentialsTtlSeconds": 3600,
      "deviceCodeTtlSeconds": 600,
      "refreshTokensEnabled": true,
      "reuseRefreshTokens": false,
      "rotateRefreshTokens": true,
      "refreshTokenReuseDetection": true
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

UPDATE platform_setting
SET value = value || '{"token-policy": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'token-policy');

```

### `V16__signup_default_member_role.sql`

```sql
-- Self-registration should assign the end-user Member role, not Tenant Admin.
UPDATE role
SET is_default = false
WHERE application_id = '22222222-2222-2222-2222-222222222201'
  AND name = 'Tenant Admin';

UPDATE role
SET is_default = true
WHERE application_id = '22222222-2222-2222-2222-222222222201'
  AND name = 'Member';

```

### `V17__external_test_application.sql`

```sql
-- OAuth SPA + public manifest/sign-up for testing SecureOne from apps outside this repo.

INSERT INTO application (id, tenant_id, name, slug, description, status, config, created_at, updated_at)
VALUES (
    '22222222-2222-2222-2222-222222222299',
    '11111111-1111-1111-1111-111111111101',
    'External Test Client',
    'external-test',
    'SPA/OAuth client for local and third-party integration testing',
    'ACTIVE',
    '{
      "type": "spa",
      "clientId": "external-test-client",
      "grantTypes": ["authorization_code", "refresh_token"],
      "scopes": ["openid", "profile", "email"],
      "redirectUris": [
        "http://127.0.0.1:5173/callback",
        "http://localhost:5173/callback",
        "http://127.0.0.1:3000/callback",
        "http://localhost:3000/callback",
        "http://127.0.0.1:8080/callback",
        "http://localhost:8080/callback"
      ],
      "postLogoutRedirectUris": [
        "http://127.0.0.1:5173/",
        "http://localhost:5173/"
      ],
      "pkceRequired": true,
      "tokenEndpointAuthMethod": "none"
    }'::jsonb,
    now(),
    now()
)
ON CONFLICT DO NOTHING;

INSERT INTO role (id, tenant_id, application_id, name, description, is_composite, is_default)
VALUES
    ('44444444-4444-4444-4444-444444444491', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222299', 'Super Admin', 'Full application control', true, false),
    ('44444444-4444-4444-4444-444444444492', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222299', 'Tenant Admin', 'Manage users and roles', false, false),
    ('44444444-4444-4444-4444-444444444493', '11111111-1111-1111-1111-111111111101',
     '22222222-2222-2222-2222-222222222299', 'Member', 'Standard end-user access', false, true)
ON CONFLICT DO NOTHING;

INSERT INTO role_composite (parent_role_id, child_role_id)
VALUES ('44444444-4444-4444-4444-444444444491', '44444444-4444-4444-4444-444444444492')
ON CONFLICT DO NOTHING;

INSERT INTO application_setting (application_id, setting_key, value)
VALUES
    (
        '22222222-2222-2222-2222-222222222299',
        'feature_flags',
        '[{"key":"self_registration","enabled":true,"rollout":100}]'::jsonb
    ),
    (
        '22222222-2222-2222-2222-222222222299',
        'public_manifest',
        '{
          "enabled": true,
          "sections": {
            "application": true,
            "authMethods": true,
            "featureFlags": true,
            "passwordPolicy": true,
            "appearance": false
          },
          "authMethodsOnlyEnabled": true
        }'::jsonb
    )
ON CONFLICT (application_id, setting_key) DO UPDATE SET value = EXCLUDED.value, updated_at = now();

```

### `V18__application_logs.sql`

```sql
-- Centralized application logs (CloudWatch-style) with session/request correlation.

CREATE TABLE application_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    level           VARCHAR(16) NOT NULL,
    logger          VARCHAR(256) NOT NULL,
    message         TEXT NOT NULL,
    session_id      VARCHAR(128),
    request_id      VARCHAR(64),
    principal       VARCHAR(320),
    tenant_id       UUID,
    application_id  UUID,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_application_log_created_at ON application_log (created_at DESC);
CREATE INDEX idx_application_log_session_id ON application_log (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_application_log_request_id ON application_log (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_application_log_level ON application_log (level);
CREATE INDEX idx_application_log_app_created ON application_log (application_id, created_at DESC)
    WHERE application_id IS NOT NULL;

ALTER TABLE login_history ADD COLUMN IF NOT EXISTS session_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_login_history_session_id ON login_history (session_id)
    WHERE session_id IS NOT NULL;

-- Permission for log viewer (seed for existing apps).
INSERT INTO permission (id, application_id, key, description)
SELECT gen_random_uuid(), a.id, 'logs:read', 'View application logs'
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM permission p WHERE p.application_id = a.id AND p.key = 'logs:read'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.application_id = r.application_id AND p.key = 'logs:read'
WHERE r.name IN ('Super Admin', 'Tenant Admin', 'Security Auditor')
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

```

### `V19__smtp_email_templates.sql`

```sql
-- Platform SMTP transport, email templates, and recipient groups (managed via admin UI).

INSERT INTO platform_setting (key, value)
VALUES
    ('smtp', '{
      "host": "",
      "port": 465,
      "security": "ssl",
      "username": "",
      "authEnabled": true
    }'::jsonb),
    ('email_templates', '{}'::jsonb)
ON CONFLICT (key) DO NOTHING;

UPDATE platform_setting
SET value = value || '{
  "recipientGroups": {
    "security": [],
    "operations": []
  }
}'::jsonb
WHERE key = 'notifications'
  AND NOT (value ? 'recipientGroups');

```

### `V20__user_credential_one_current.sql`

```sql
-- Each user must have at most one current password credential.
UPDATE user_credential stale
SET is_current = false
WHERE stale.is_current = true
  AND stale.id NOT IN (
      SELECT DISTINCT ON (user_id) id
      FROM user_credential
      WHERE is_current = true
      ORDER BY user_id, created_at DESC, id DESC
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_credential_one_current
    ON user_credential (user_id)
    WHERE is_current = true;

```

### `V21__restore_dev_tenant_admin.sql`

```sql
-- Restore dev tenant admin for admin console login (idempotent).

INSERT INTO user_account (tenant_id, email, email_verified, username, display_name, status, type, created_at, updated_at)
SELECT t.id, 'sarah.chen@acme.com', true, 'schen', 'Sarah Chen', 'ACTIVE', 'USER', now(), now()
FROM tenant t
WHERE t.slug = 'acme'
  AND NOT EXISTS (
      SELECT 1 FROM user_account u
      WHERE u.tenant_id = t.id AND u.email = 'sarah.chen@acme.com'
  );

INSERT INTO user_credential (user_id, password_hash, algorithm, is_current)
SELECT u.id, '$2a$10$l4rw.ZW0bub9AMUDZp5BoOeblXp6zfo7fohXX2Ws5/jLeIlj1hmwS', 'bcrypt', true
FROM user_account u
JOIN tenant t ON t.id = u.tenant_id
WHERE t.slug = 'acme' AND u.email = 'sarah.chen@acme.com'
  AND NOT EXISTS (
      SELECT 1 FROM user_credential c WHERE c.user_id = u.id AND c.is_current = true
  );

INSERT INTO user_role (user_id, role_id, granted_at)
SELECT u.id, '44444444-4444-4444-4444-444444444402', now()
FROM user_account u
JOIN tenant t ON t.id = u.tenant_id
WHERE t.slug = 'acme' AND u.email = 'sarah.chen@acme.com'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = '44444444-4444-4444-4444-444444444402'
  );

```

### `V22__dev_reset_acme_operator_password.sql`

```sql
-- Dev-only: restore known password (SecureOne123!) for the original Acme seeded operator
-- when the account email was changed (e.g. tyagidav97@gmail.com) and password no longer matches docs.

UPDATE user_credential c
SET password_hash = '$2a$10$l4rw.ZW0bub9AMUDZp5BoOeblXp6zfo7fohXX2Ws5/jLeIlj1hmwS',
    algorithm = 'bcrypt',
    is_current = true
FROM user_account u
JOIN tenant t ON t.id = u.tenant_id
WHERE c.user_id = u.id
  AND c.is_current = true
  AND t.slug = 'acme'
  AND u.email = 'tyagidav97@gmail.com';

INSERT INTO user_credential (user_id, password_hash, algorithm, is_current)
SELECT u.id, '$2a$10$l4rw.ZW0bub9AMUDZp5BoOeblXp6zfo7fohXX2Ws5/jLeIlj1hmwS', 'bcrypt', true
FROM user_account u
JOIN tenant t ON t.id = u.tenant_id
WHERE t.slug = 'acme'
  AND u.email = 'tyagidav97@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM user_credential c WHERE c.user_id = u.id AND c.is_current = true
  );

```

### `V23__admin_console_access.sql`

```sql
-- Admin console login roles (separate from end-user application RBAC).
-- APPLICATION_ADMIN: one assigned application
-- TENANT_ADMIN: explicitly assigned applications only
-- TENANT_SUPER_ADMIN: all applications in the tenant

CREATE TABLE admin_console_access (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id  UUID REFERENCES application(id) ON DELETE CASCADE,
    role_type       VARCHAR(32) NOT NULL,
    granted_by      UUID REFERENCES user_account(id) ON DELETE SET NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT admin_console_access_role_type_chk CHECK (
        role_type IN ('APPLICATION_ADMIN', 'TENANT_ADMIN', 'TENANT_SUPER_ADMIN')
    ),
    CONSTRAINT admin_console_access_scope_chk CHECK (
        (role_type = 'TENANT_SUPER_ADMIN' AND application_id IS NULL)
        OR (role_type IN ('APPLICATION_ADMIN', 'TENANT_ADMIN') AND application_id IS NOT NULL)
    )
);

CREATE INDEX idx_admin_console_access_user ON admin_console_access(user_id);
CREATE INDEX idx_admin_console_access_tenant ON admin_console_access(tenant_id);
CREATE INDEX idx_admin_console_access_app ON admin_console_access(application_id);

CREATE UNIQUE INDEX uq_admin_console_access_app_scope
    ON admin_console_access(user_id, tenant_id, application_id, role_type)
    WHERE application_id IS NOT NULL;

CREATE UNIQUE INDEX uq_admin_console_access_tenant_super
    ON admin_console_access(user_id, tenant_id)
    WHERE role_type = 'TENANT_SUPER_ADMIN';

-- Application Admin system role (per application) for in-app RBAC permissions.
INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Application Admin',
       'Manage users and roles for this application in SecureOne Admin', true, false, false
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM role r
    WHERE r.application_id = a.id AND r.name = 'Application Admin'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.application_id = r.application_id
WHERE r.name = 'Application Admin'
  AND p.key IN ('user:read', 'user:write', 'role:read', 'app:read', 'audit:read', 'session:read')
ON CONFLICT DO NOTHING;

-- Backfill existing Tenant Admin RBAC grants as TENANT_ADMIN console access.
INSERT INTO admin_console_access (user_id, tenant_id, application_id, role_type, granted_at)
SELECT ur.user_id, r.tenant_id, r.application_id, 'TENANT_ADMIN', COALESCE(ur.granted_at, now())
FROM user_role ur
JOIN role r ON r.id = ur.role_id
WHERE ur.user_id IS NOT NULL
  AND r.name = 'Tenant Admin'
ON CONFLICT DO NOTHING;

-- Dev: Acme tenant super admin for sarah.chen@acme.com
INSERT INTO admin_console_access (user_id, tenant_id, application_id, role_type, granted_at)
SELECT u.id, t.id, NULL, 'TENANT_SUPER_ADMIN', now()
FROM user_account u
JOIN tenant t ON t.id = u.tenant_id
WHERE t.slug = 'acme' AND u.email = 'sarah.chen@acme.com'
ON CONFLICT DO NOTHING;

```

### `V24__dedupe_admin_console_access.sql`

```sql
-- Per-app console access is redundant when the user already has tenant super admin.
DELETE FROM admin_console_access scoped
USING admin_console_access super
WHERE scoped.user_id = super.user_id
  AND scoped.tenant_id = super.tenant_id
  AND super.role_type = 'TENANT_SUPER_ADMIN'
  AND super.application_id IS NULL
  AND scoped.application_id IS NOT NULL
  AND scoped.role_type IN ('TENANT_ADMIN', 'APPLICATION_ADMIN');

```

### `V25__remove_dev_seed_data.sql`

```sql
-- Remove dev seed tenants (acme, globex) and all dependent rows (CASCADE).
-- Platform super-admin remains in-memory (application config), not in these tables.

DELETE FROM tenant WHERE slug IN ('acme', 'globex');

-- Clear dev notification recipients from platform settings.
UPDATE platform_setting
SET value = jsonb_set(COALESCE(value, '{}'::jsonb), '{adminRecipients}', '[]'::jsonb, true),
    updated_at = now()
WHERE key = 'notifications';

```

### `V26__purge_dev_sample_rows.sql`

```sql
-- Remove leftover dev sample rows (audit/login history have no tenant FK CASCADE).

DELETE FROM audit_log
WHERE tenant_id IN ('11111111-1111-1111-1111-111111111101', '11111111-1111-1111-1111-111111111102')
   OR id IN (
       'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
       'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'
   );

DELETE FROM login_history
WHERE tenant_id IN ('11111111-1111-1111-1111-111111111101', '11111111-1111-1111-1111-111111111102')
   OR id IN (
       'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
       'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2'
   );

-- Ensure notification recipients are empty after dev tenant removal.
UPDATE platform_setting
SET value = jsonb_set(COALESCE(value, '{}'::jsonb), '{adminRecipients}', '[]'::jsonb, true),
    updated_at = now()
WHERE key = 'notifications';

```

### `V27__finalize_dev_data_removal.sql`

```sql
-- Final cleanup after V25/V26: remove orphan rows tied to deleted dev tenants.

DELETE FROM admin_console_access
WHERE user_id NOT IN (SELECT id FROM user_account)
   OR tenant_id NOT IN (SELECT id FROM tenant);

DELETE FROM user_role
WHERE user_id IS NOT NULL
  AND user_id NOT IN (SELECT id FROM user_account);

DELETE FROM user_application
WHERE user_id NOT IN (SELECT id FROM user_account)
   OR application_id NOT IN (SELECT id FROM application);

```

### `V28__seed_default_roles_all_apps.sql`

```sql
-- Backfill standard RBAC roles for applications created without default role seeds.

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Tenant Admin', 'Full tenant administration', true, false, false
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Tenant Admin'
);

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Member', 'Standard end-user access', true, true, false
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Member'
);

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Application Admin',
       'Manage users and roles for this application in SecureOne Admin', true, false, false
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Application Admin'
);

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Security Auditor', 'Read-only access to audit and identity data', true, false, false
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Security Auditor'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.application_id = r.application_id
WHERE (
    (r.name = 'Tenant Admin' AND p.key IN (
        'user:read', 'user:write', 'user:delete', 'role:read', 'role:write',
        'app:read', 'app:write', 'settings:write', 'audit:read', 'session:read'))
    OR (r.name = 'Member' AND p.key = 'user:read')
    OR (r.name = 'Application Admin' AND p.key IN (
        'user:read', 'user:write', 'role:read', 'app:read', 'audit:read', 'session:read'))
    OR (r.name = 'Security Auditor' AND p.key IN ('audit:read', 'user:read', 'role:read', 'session:read'))
)
AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

```

### `V29__tenant_user_roster.sql`

```sql
-- Tenant roster: operators explicitly manage these users at tenant scope.
-- Application invites, signups, and self-registration do not add rows here.

CREATE TABLE tenant_user_roster (
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id)
);

CREATE INDEX idx_tenant_user_roster_user ON tenant_user_roster(user_id);

-- Keep existing admin console operators visible in tenant workspace.
INSERT INTO tenant_user_roster (tenant_id, user_id)
SELECT DISTINCT tenant_id, user_id
FROM admin_console_access
ON CONFLICT DO NOTHING;

```

### `V30__repair_invited_application_members.sql`

```sql
-- Repair invited users stuck without application membership or active status.

UPDATE user_account u
SET status = 'ACTIVE', email_verified = true, updated_at = now()
WHERE u.status = 'PENDING'
  AND EXISTS (
      SELECT 1 FROM user_credential uc WHERE uc.user_id = u.id AND uc.is_current = true
  )
  AND EXISTS (
      SELECT 1 FROM email_token et
      WHERE et.user_id = u.id AND et.type = 'SET_PASSWORD' AND et.consumed_at IS NOT NULL
  );

INSERT INTO user_application (user_id, application_id)
SELECT u.id, (u.attributes->>'pendingInviteApplicationId')::uuid
FROM user_account u
WHERE u.attributes ? 'pendingInviteApplicationId'
  AND NOT EXISTS (
      SELECT 1 FROM user_application ua
      WHERE ua.user_id = u.id
        AND ua.application_id = (u.attributes->>'pendingInviteApplicationId')::uuid
  );

INSERT INTO user_application (user_id, application_id)
SELECT u.id, a.id
FROM user_account u
JOIN application a ON a.tenant_id = u.tenant_id
WHERE NOT EXISTS (SELECT 1 FROM user_application ua WHERE ua.user_id = u.id)
  AND EXISTS (SELECT 1 FROM user_credential uc WHERE uc.user_id = u.id AND uc.is_current = true)
  AND (SELECT COUNT(*) FROM application a2 WHERE a2.tenant_id = u.tenant_id) = 1
ON CONFLICT DO NOTHING;

```

### `V31__tenant_roster_metadata.sql`

```sql
-- Track how users were added to the tenant roster.

ALTER TABLE tenant_user_roster
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'direct',
    ADD COLUMN source_application_id UUID REFERENCES application(id) ON DELETE SET NULL,
    ADD COLUMN added_by UUID REFERENCES user_account(id) ON DELETE SET NULL;

UPDATE tenant_user_roster r
SET source = 'console_access'
WHERE EXISTS (
    SELECT 1
    FROM admin_console_access a
    WHERE a.tenant_id = r.tenant_id
      AND a.user_id = r.user_id
      AND (a.expires_at IS NULL OR a.expires_at > now())
);

CREATE INDEX idx_tenant_user_roster_source ON tenant_user_roster(tenant_id, source);

```

### `V32__admin_console_feature_overrides.sql`

```sql
-- Per-user grant/deny overrides for admin console features (beyond role defaults).
CREATE TABLE admin_console_feature_override (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    feature_key VARCHAR(64) NOT NULL,
    effect      VARCHAR(8) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT admin_console_feature_override_effect_chk CHECK (effect IN ('GRANT', 'DENY')),
    CONSTRAINT uq_admin_console_feature_override UNIQUE (user_id, tenant_id, feature_key)
);

CREATE INDEX idx_admin_console_feature_override_user ON admin_console_feature_override(user_id);
CREATE INDEX idx_admin_console_feature_override_tenant ON admin_console_feature_override(tenant_id);

```

### `V33__cleanup_orphan_console_roster_rows.sql`

```sql
-- Remove tenant roster rows that were created for console access but no longer have active console access.
DELETE FROM tenant_user_roster tur
WHERE tur.source = 'console_access'
  AND NOT EXISTS (
    SELECT 1
    FROM admin_console_access aca
    WHERE aca.user_id = tur.user_id
      AND aca.tenant_id = tur.tenant_id
      AND (aca.expires_at IS NULL OR aca.expires_at > NOW())
  );

```

### `V34__tenant_rbac.sql`

```sql
-- Tenant-scoped RBAC (platform / operator governance â€” separate from per-application product RBAC).

CREATE TABLE tenant_permission (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    key         VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    UNIQUE (tenant_id, key)
);
CREATE INDEX idx_tenant_permission_tenant ON tenant_permission(tenant_id);

CREATE TABLE tenant_role (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_system   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_tenant_role_tenant ON tenant_role(tenant_id);

CREATE TABLE tenant_role_permission (
    role_id       UUID NOT NULL REFERENCES tenant_role(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES tenant_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE tenant_role_application (
    role_id        UUID NOT NULL REFERENCES tenant_role(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, application_id)
);

CREATE TABLE user_tenant_role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES tenant_role(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, role_id)
);
CREATE INDEX idx_user_tenant_role_user ON user_tenant_role(user_id);
CREATE INDEX idx_user_tenant_role_role ON user_tenant_role(role_id);

```

### `V35__seed_tenant_rbac_existing.sql`

```sql
-- Backfill tenant RBAC catalog for existing tenants (roles created on first API access if missing).

INSERT INTO tenant_permission (id, tenant_id, key, description)
SELECT gen_random_uuid(), t.id, e.key, e.description
FROM tenant t
CROSS JOIN (
    VALUES
        ('tenant:read', 'View tenant metadata and roster'),
        ('tenant:manage', 'Edit tenant settings and roster'),
        ('operator:read', 'View tenant operators and console access'),
        ('operator:manage', 'Grant or revoke operator console access'),
        ('application:access', 'Assign users to applications'),
        ('application:manage', 'Create and configure applications'),
        ('console:users', 'Admin console â€” Users section'),
        ('console:roles', 'Admin console â€” Roles section'),
        ('console:permissions', 'Admin console â€” Permissions section'),
        ('console:settings', 'Admin console â€” Settings section'),
        ('console:audit', 'Admin console â€” Audit log'),
        ('console:logs', 'Admin console â€” Application logs'),
        ('console:sessions', 'Admin console â€” Sessions')
) AS e(key, description)
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_permission p WHERE p.tenant_id = t.id AND p.key = e.key
);

INSERT INTO tenant_role (id, tenant_id, name, description, is_system)
SELECT gen_random_uuid(), t.id, r.name, r.description, true
FROM tenant t
CROSS JOIN (
    VALUES
        ('Tenant Administrator', 'Full tenant governance â€” operators, applications, and console features'),
        ('Application Operator', 'Manage users and settings for assigned applications'),
        ('Tenant Auditor', 'Read-only access to tenant roster, audit, and logs')
) AS r(name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_role tr WHERE tr.tenant_id = t.id AND tr.name = r.name
);

INSERT INTO tenant_role_permission (role_id, permission_id)
SELECT tr.id, tp.id
FROM tenant_role tr
JOIN tenant_permission tp ON tp.tenant_id = tr.tenant_id
WHERE (
    (tr.name = 'Tenant Administrator' AND tp.key IN (
        'tenant:read', 'tenant:manage', 'operator:read', 'operator:manage',
        'application:access', 'application:manage',
        'console:users', 'console:roles', 'console:permissions', 'console:settings',
        'console:audit', 'console:logs', 'console:sessions'))
    OR (tr.name = 'Application Operator' AND tp.key IN (
        'tenant:read', 'operator:read', 'application:access',
        'console:users', 'console:roles', 'console:settings', 'console:audit', 'console:sessions'))
    OR (tr.name = 'Tenant Auditor' AND tp.key IN (
        'tenant:read', 'operator:read', 'console:audit', 'console:logs', 'console:sessions'))
)
AND NOT EXISTS (
    SELECT 1 FROM tenant_role_permission rp
    WHERE rp.role_id = tr.id AND rp.permission_id = tp.id
);

```

### `V36__tenant_console_role_features.sql`

```sql
-- Per-tenant default console sections for system console operator roles.

CREATE TABLE tenant_console_role_feature (
    tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    role_key    VARCHAR(80) NOT NULL,
    feature_key VARCHAR(80) NOT NULL,
    PRIMARY KEY (tenant_id, role_key, feature_key)
);
CREATE INDEX idx_tenant_console_role_feature_tenant ON tenant_console_role_feature(tenant_id);

```

### `V37__mark_tenant_rbac_system_roles.sql`

```sql
-- Catalog tenant RBAC roles are templates (not shown as custom roles in the governance UI).

UPDATE tenant_role
SET is_system = true
WHERE name IN ('Tenant Administrator', 'Application Operator', 'Tenant Auditor')
  AND is_system = false;

```

### `V38__platform_notifications_independent.sql`

```sql
-- Platform notifications are operator alerts only (separate from application notification settings).

UPDATE platform_setting
SET value = (value - 'userEmailEnabled' - 'pushEnabled' - 'recipientGroups')
WHERE key = 'notifications';

```

### `V39__user_credential_expires_at.sql`

```sql
-- Password expiry is stamped when the credential is created (from active policy at set time).

ALTER TABLE user_credential
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

```

### `V40__password_expiry_notifications.sql`

```sql
-- Track password expiry warning and expired-reset emails per credential row.

ALTER TABLE user_credential
    ADD COLUMN IF NOT EXISTS expiry_warning_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expiry_expired_notice_sent_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_user_credential_expires_at
    ON user_credential (expires_at)
    WHERE is_current = true AND expires_at IS NOT NULL;

```

### `V41__trim_feature_flag_catalog.sql`

```sql
-- Trim feature flag catalog to keys with application runtime or admin UI effect.

UPDATE platform_setting
SET value = COALESCE(
    (
        SELECT jsonb_agg(elem ORDER BY elem->>'key')
        FROM jsonb_array_elements(value) AS elem
        WHERE elem->>'key' IN (
            'self_service_recovery',
            'self_registration',
            'ldap',
            'notification_email_test_ui'
        )
    ),
    '[]'::jsonb
)
WHERE key = 'feature_flags'
  AND jsonb_typeof(value) = 'array';

UPDATE application_setting
SET value = COALESCE(
    (
        SELECT jsonb_agg(elem ORDER BY elem->>'key')
        FROM jsonb_array_elements(value) AS elem
        WHERE elem->>'key' IN (
            'self_service_recovery',
            'self_registration',
            'ldap',
            'notification_email_test_ui'
        )
    ),
    '[]'::jsonb
)
WHERE setting_key = 'feature_flags'
  AND jsonb_typeof(value) = 'array';

```

### `V42__application_rbac_groups.sql`

```sql
-- Application-scoped RBAC groups: bundle roles and assign users to groups.

CREATE TABLE rbac_group (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (application_id, name)
);
CREATE INDEX idx_rbac_group_application ON rbac_group(application_id);

CREATE TABLE rbac_group_role (
    group_id UUID NOT NULL REFERENCES rbac_group(id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);
CREATE INDEX idx_rbac_group_role_role ON rbac_group_role(role_id);

CREATE TABLE rbac_group_member (
    group_id UUID NOT NULL REFERENCES rbac_group(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_rbac_group_member_user ON rbac_group_member(user_id);

```

### `V43__tenant_console_groups.sql`

```sql
-- Add Groups console section to tenant RBAC and operator role defaults.

INSERT INTO tenant_permission (id, tenant_id, key, description)
SELECT gen_random_uuid(), t.id, 'console:groups', 'Admin console â€” Groups section'
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_permission p WHERE p.tenant_id = t.id AND p.key = 'console:groups'
);

INSERT INTO tenant_role_permission (role_id, permission_id)
SELECT tr.id, tp.id
FROM tenant_role tr
JOIN tenant_permission tp ON tp.tenant_id = tr.tenant_id AND tp.key = 'console:groups'
WHERE tr.name IN ('Tenant Administrator', 'Application Operator')
AND NOT EXISTS (
    SELECT 1 FROM tenant_role_permission rp
    WHERE rp.role_id = tr.id AND rp.permission_id = tp.id
);

-- Tenants with customized console operator defaults: grant groups where roles is already enabled.
INSERT INTO tenant_console_role_feature (tenant_id, role_key, feature_key)
SELECT DISTINCT tcrf.tenant_id, tcrf.role_key, 'groups'
FROM tenant_console_role_feature tcrf
WHERE tcrf.feature_key = 'roles'
AND tcrf.role_key IN ('APPLICATION_ADMIN', 'TENANT_ADMIN', 'TENANT_SUPER_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM tenant_console_role_feature existing
    WHERE existing.tenant_id = tcrf.tenant_id
      AND existing.role_key = tcrf.role_key
      AND existing.feature_key = 'groups'
);

```

### `V44__reseed_platform_settings.sql`

```sql
-- Re-seed platform_setting rows if the table was truncated manually (Flyway history unchanged).

INSERT INTO platform_setting (key, value)
VALUES
    ('notifications', '{
      "emailEnabled": true,
      "auditAlertsEnabled": true,
      "securityAlertsEnabled": true,
      "adminRecipients": []
    }'::jsonb),
    ('email', '{
      "fromName": "SecureOne",
      "fromAddress": "noreply@secureone.local",
      "replyTo": "support@secureone.local"
    }'::jsonb),
    ('smtp', '{
      "host": "",
      "port": 465,
      "security": "ssl",
      "username": "",
      "authEnabled": true
    }'::jsonb),
    ('email_templates', '{}'::jsonb),
    ('app_settings_exposure', '{
      "notifications": true,
      "email": true,
      "auth-methods": true,
      "password-policy": true,
      "feature-flags": true,
      "appearance": false,
      "user-directory": true,
      "public-manifest": true,
      "token-policy": true
    }'::jsonb)
ON CONFLICT (key) DO NOTHING;

INSERT INTO platform_setting (key, value)
VALUES
    ('auth_methods', '[
      {"id":"m_password","name":"Password","description":"Username + password","enabled":true,"category":"primary","implemented":true},
      {"id":"m_passkey","name":"Passkeys (WebAuthn)","description":"Phishing-resistant sign-in","enabled":true,"category":"primary","implemented":false},
      {"id":"m_magic","name":"Magic Link","description":"Email passwordless login","enabled":true,"category":"primary","implemented":true},
      {"id":"m_totp","name":"TOTP Authenticator","description":"Authenticator app codes","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_sms","name":"SMS OTP","description":"SMS one-time codes","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_email_otp","name":"Email OTP","description":"Email one-time codes at login","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_push","name":"Push Notification","description":"Approve on device","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_google","name":"Google","description":"Google OIDC","enabled":true,"category":"federation","implemented":false},
      {"id":"m_github","name":"GitHub","description":"GitHub OAuth","enabled":true,"category":"federation","implemented":false},
      {"id":"m_saml","name":"SAML 2.0","description":"Enterprise SSO","enabled":false,"category":"federation","implemented":false},
      {"id":"m_oidc","name":"External OIDC","description":"OIDC federation","enabled":false,"category":"federation","implemented":false}
    ]'::jsonb),
    ('password_policy', '{
      "minLength": 12,
      "requireUppercase": true,
      "requireNumber": true,
      "requireSymbol": true,
      "expiryDays": 0,
      "historyCount": 5,
      "hashAlgorithm": "bcrypt"
    }'::jsonb),
    ('feature_flags', '[
      {"key":"self_service_recovery","name":"Self-service Recovery","description":"Application-scoped forgot-password","enabled":true,"rollout":100,"category":"identity"},
      {"key":"self_registration","name":"Self Registration","description":"Public user signup","enabled":false,"rollout":0,"category":"identity"},
      {"key":"ldap","name":"LDAP / AD","description":"LDAP user import","enabled":false,"rollout":0,"category":"identity"},
      {"key":"notification_email_test_ui","name":"Email test console","description":"SMTP test panel under Notifications","enabled":true,"rollout":100,"category":"notifications"}
    ]'::jsonb),
    ('user_directory', '{
      "importEnabled": false,
      "exportEnabled": false,
      "sources": {"csv": {"enabled": true}, "excel": {"enabled": true}, "ldap": {"enabled": false}},
      "ldap": {"host": "", "port": 389, "baseDn": "", "bindDn": "", "bindPassword": "", "userFilter": "(mail={0})", "useTls": true}
    }'::jsonb),
    ('public_manifest_defaults', '{
      "enabled": false,
      "sections": {"application": true, "authMethods": true, "featureFlags": true, "passwordPolicy": true, "appearance": false},
      "authMethodsOnlyEnabled": true
    }'::jsonb),
    ('token_policy', '{
      "accessTokenTtlSeconds": 3600,
      "refreshTokenTtlSeconds": 604800,
      "authorizationCodeTtlSeconds": 300,
      "idTokenTtlSeconds": 3600,
      "clientCredentialsTtlSeconds": 3600,
      "deviceCodeTtlSeconds": 600,
      "refreshTokensEnabled": true,
      "reuseRefreshTokens": false,
      "rotateRefreshTokens": true,
      "refreshTokenReuseDetection": true
    }'::jsonb),
    ('appearance', '{}'::jsonb)
ON CONFLICT (key) DO NOTHING;

```

### `V45__platform_schema_oauth_client.sql`

```sql
-- Move shared tables from public â†’ platform schema, add oauth_client + application schema registry.

CREATE SCHEMA IF NOT EXISTS platform;

-- Drop empty platform tables left from a partial migration so public copies can be moved.
DO $$
DECLARE
    tbl text;
BEGIN
    FOR tbl IN
        SELECT p.tablename
        FROM pg_tables p
        INNER JOIN pg_tables pub ON pub.tablename = p.tablename AND pub.schemaname = 'public'
        WHERE p.schemaname = 'platform'
          AND p.tablename NOT IN ('flyway_schema_history')
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS platform.%I CASCADE', tbl);
    END LOOP;
END $$;

DO $$
DECLARE
    tbl text;
BEGIN
    FOR tbl IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename <> 'spatial_ref_sys'
          AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I SET SCHEMA platform', tbl);
    END LOOP;
END $$;

ALTER TABLE platform.application
    ADD COLUMN IF NOT EXISTS schema_name VARCHAR(63);

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_schema_name
    ON platform.application (schema_name)
    WHERE schema_name IS NOT NULL;

CREATE TABLE IF NOT EXISTS platform.application_schema (
    application_id UUID PRIMARY KEY REFERENCES platform.application (id) ON DELETE CASCADE,
    schema_name    VARCHAR(63) NOT NULL UNIQUE,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    flyway_version VARCHAR(64),
    provisioned_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform.oauth_client (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              UUID NOT NULL REFERENCES platform.application (id) ON DELETE CASCADE,
    client_id                   VARCHAR(150) NOT NULL UNIQUE,
    client_secret               VARCHAR(500),
    client_name                 VARCHAR(255) NOT NULL,
    type                        VARCHAR(32) NOT NULL DEFAULT 'web',
    redirect_uris               JSONB NOT NULL DEFAULT '[]'::jsonb,
    post_logout_redirect_uris   JSONB NOT NULL DEFAULT '[]'::jsonb,
    grant_types                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    scopes                      JSONB NOT NULL DEFAULT '[]'::jsonb,
    token_endpoint_auth_method  VARCHAR(64) NOT NULL DEFAULT 'client_secret_basic',
    require_pkce                BOOLEAN NOT NULL DEFAULT true,
    status                      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_oauth_client_application ON platform.oauth_client (application_id);

-- Backfill OAuth clients from legacy application.config JSONB.
INSERT INTO platform.oauth_client (
    id,
    application_id,
    client_id,
    client_secret,
    client_name,
    type,
    redirect_uris,
    post_logout_redirect_uris,
    grant_types,
    scopes,
    token_endpoint_auth_method,
    require_pkce,
    status,
    created_at,
    updated_at)
SELECT
    gen_random_uuid(),
    a.id,
    COALESCE(NULLIF(TRIM(a.config ->> 'clientId'), ''), a.slug),
    NULLIF(a.config ->> 'clientSecret', ''),
    a.name,
    COALESCE(NULLIF(TRIM(a.config ->> 'type'), ''), 'web'),
    COALESCE(a.config -> 'redirectUris', '[]'::jsonb),
    COALESCE(a.config -> 'postLogoutRedirectUris', '[]'::jsonb),
    COALESCE(a.config -> 'grantTypes', '[]'::jsonb),
    COALESCE(a.config -> 'scopes', '[]'::jsonb),
    COALESCE(NULLIF(TRIM(a.config ->> 'tokenEndpointAuthMethod'), ''), 'client_secret_basic'),
    COALESCE((a.config ->> 'pkceRequired')::boolean, true),
    a.status,
    a.created_at,
    a.updated_at
FROM platform.application a
WHERE (
        a.config ? 'clientId'
        OR a.config ? 'type'
        OR a.config ? 'grantTypes'
        OR a.config ? 'redirectUris')
  AND NOT EXISTS (
        SELECT 1 FROM platform.oauth_client oc WHERE oc.application_id = a.id);

-- Strip OAuth protocol keys from application.config; retain non-OAuth keys (e.g. tokenPolicy).
UPDATE platform.application
SET config = config
    - 'type'
    - 'clientId'
    - 'clientSecret'
    - 'grantTypes'
    - 'scopes'
    - 'redirectUris'
    - 'postLogoutRedirectUris'
    - 'pkceRequired'
    - 'tokenEndpointAuthMethod'
WHERE config ?| ARRAY[
    'type', 'clientId', 'clientSecret', 'grantTypes', 'scopes',
    'redirectUris', 'postLogoutRedirectUris', 'pkceRequired', 'tokenEndpointAuthMethod'];

```

### `V46__otp_verification_and_phone.sql`

```sql
-- OTP challenges (audit trail) + verification policy + SMS settings + phone uniqueness

CREATE TABLE IF NOT EXISTS otp_challenge (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES user_account(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    application_id  UUID,
    purpose         VARCHAR(32) NOT NULL,
    channel         VARCHAR(16) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    code_hash       VARCHAR(64) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      VARCHAR(45),
    user_agent      TEXT
);

CREATE INDEX IF NOT EXISTS idx_otp_challenge_active
    ON otp_challenge (user_id, purpose)
    WHERE consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_otp_challenge_expires
    ON otp_challenge (expires_at)
    WHERE consumed_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_account_tenant_phone
    ON user_account (tenant_id, phone)
    WHERE phone IS NOT NULL AND phone <> '';

INSERT INTO platform_setting (key, value)
VALUES (
    'verification_policy',
    '{
      "signup": {
        "requiredChannels": ["email"],
        "optionalChannels": ["phone"],
        "defaultMode": "link",
        "allowedModes": ["link", "otp"],
        "allowUserChannelChoice": false,
        "otpLength": 6,
        "otpTtlSeconds": 600,
        "maxAttempts": 5,
        "resendCooldownSeconds": 60
      },
      "login": {
        "identifierTypes": ["email", "phone"],
        "passwordlessModes": ["email_otp", "phone_otp", "magic_link"],
        "mfaRequired": "optional"
      },
      "recovery": {
        "passwordResetMode": "link",
        "allowedChannels": ["email"],
        "fallbackToLink": true
      }
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

INSERT INTO platform_setting (key, value)
VALUES (
    'sms',
    '{
      "provider": "log",
      "twilioAccountSid": "",
      "twilioAuthToken": "",
      "twilioFromNumber": "",
      "enabled": false
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

-- Expose verification-policy to applications (merge into app_settings_exposure if present)
UPDATE platform_setting
SET value = value || '{"verification-policy": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'verification-policy');
```

### `V47__expose_sms_to_applications.sql`

```sql
-- Expose per-application SMS provider settings (merge into app_settings_exposure if present)
UPDATE platform_setting
SET value = value || '{"sms": true}'::jsonb
WHERE key = 'app_settings_exposure'
  AND NOT (value ? 'sms');
```

### `V48__recovery_otp_and_verification_policy.sql`

```sql
-- Expand verification policy (recovery OTP modes, login OTP params) and add recovery_otp feature flag

UPDATE platform_setting
SET value = jsonb_set(
    value,
    '{recovery}',
    COALESCE(value->'recovery', '{}'::jsonb)
        || '{
          "allowedModes": ["link", "otp"],
          "allowedChannels": ["email", "phone"],
          "allowUserChannelChoice": true,
          "requireVerifiedChannel": true,
          "otpLength": 6,
          "otpTtlSeconds": 600,
          "maxAttempts": 5,
          "resendCooldownSeconds": 60
        }'::jsonb,
    true)
WHERE key = 'verification_policy';

UPDATE platform_setting
SET value = jsonb_set(
    value,
    '{login}',
    COALESCE(value->'login', '{}'::jsonb)
        || '{
          "otpLength": 6,
          "otpTtlSeconds": 600,
          "maxAttempts": 5,
          "resendCooldownSeconds": 60
        }'::jsonb,
    true)
WHERE key = 'verification_policy';

-- Merge recovery_otp into feature_flags catalog if missing
UPDATE platform_setting
SET value = (
    SELECT jsonb_agg(flag)
    FROM (
        SELECT flag
        FROM jsonb_array_elements(value) AS flag
        UNION ALL
        SELECT '{"key":"recovery_otp","name":"Recovery OTP","description":"Password reset via email/SMS OTP","enabled":true,"rollout":100,"category":"identity"}'::jsonb
        WHERE NOT EXISTS (
            SELECT 1 FROM jsonb_array_elements(value) f WHERE f->>'key' = 'recovery_otp'
        )
    ) merged
)
WHERE key = 'feature_flags'
  AND NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(value) f WHERE f->>'key' = 'recovery_otp'
  );
```

### `V49__oauth2_authorization_jdbc.sql`

```sql
-- Spring Authorization Server JDBC persistence (PostgreSQL: blob columns → text).
-- Used by JdbcOAuth2AuthorizationService for multi-replica OAuth flows.

CREATE TABLE oauth2_authorization (
    id                           VARCHAR(100)  NOT NULL,
    registered_client_id         VARCHAR(100)  NOT NULL,
    principal_name               VARCHAR(200)  NOT NULL,
    authorization_grant_type     VARCHAR(100)  NOT NULL,
    authorized_scopes            VARCHAR(1000) DEFAULT NULL,
    attributes                   TEXT          DEFAULT NULL,
    state                        VARCHAR(500)  DEFAULT NULL,
    authorization_code_value     TEXT          DEFAULT NULL,
    authorization_code_issued_at TIMESTAMP     DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP    DEFAULT NULL,
    authorization_code_metadata  TEXT          DEFAULT NULL,
    access_token_value           TEXT          DEFAULT NULL,
    access_token_issued_at       TIMESTAMP     DEFAULT NULL,
    access_token_expires_at      TIMESTAMP     DEFAULT NULL,
    access_token_metadata        TEXT          DEFAULT NULL,
    access_token_type            VARCHAR(100)  DEFAULT NULL,
    access_token_scopes          VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value          TEXT          DEFAULT NULL,
    oidc_id_token_issued_at      TIMESTAMP     DEFAULT NULL,
    oidc_id_token_expires_at     TIMESTAMP     DEFAULT NULL,
    oidc_id_token_metadata       TEXT          DEFAULT NULL,
    refresh_token_value          TEXT          DEFAULT NULL,
    refresh_token_issued_at      TIMESTAMP     DEFAULT NULL,
    refresh_token_expires_at     TIMESTAMP     DEFAULT NULL,
    refresh_token_metadata       TEXT          DEFAULT NULL,
    user_code_value              TEXT          DEFAULT NULL,
    user_code_issued_at          TIMESTAMP     DEFAULT NULL,
    user_code_expires_at         TIMESTAMP     DEFAULT NULL,
    user_code_metadata           TEXT          DEFAULT NULL,
    device_code_value            TEXT          DEFAULT NULL,
    device_code_issued_at        TIMESTAMP     DEFAULT NULL,
    device_code_expires_at       TIMESTAMP     DEFAULT NULL,
    device_code_metadata         TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
```

### `V50__signing_keys.sql`

```sql
-- OAuth2 JWT signing keys: persisted in platform_setting (key = oauth2_signing_jwk) at runtime.
-- Production: set SECUREONE_JWK_PRIVATE_KEY_PEM or pre-seed that row before first deploy.

SELECT 1;
```

### `V52__mfa_factor_extended.sql`

```sql
-- Extend MFA factors for TOTP secrets, recovery codes, and WebAuthn (phase B).

ALTER TABLE mfa_factor
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN mfa_factor.secret_encrypted IS 'AES-GCM encrypted TOTP seed or recovery payload';
COMMENT ON COLUMN mfa_factor.metadata IS 'Recovery code hashes, WebAuthn credential fields (future)';
```

### `V53__mfa_policy.sql`

```sql
-- Platform MFA policy defaults (step-up behavior complements verification_policy.login.mfaRequired).

INSERT INTO platform_setting (key, value)
VALUES (
    'mfa_policy',
    '{
      "stepUpAfterPassword": true,
      "recoveryCodesPerEnrollment": 10,
      "challengeTtlSeconds": 300,
      "maxChallengeAttempts": 5
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;
```

### `V54__tenant_rls_policies.sql`

```sql
-- PostgreSQL row-level security (defense in depth).
-- When app.tenant_id is empty, platform operators see all rows.
-- When set (per-request via set_config), only matching tenant rows are visible.

CREATE OR REPLACE FUNCTION secureone_rls_bypass() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT COALESCE(NULLIF(current_setting('app.tenant_id', true), ''), '') = '';
$$;

CREATE OR REPLACE FUNCTION secureone_tenant_matches(row_tenant_id uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT secureone_rls_bypass()
        OR row_tenant_id::text = current_setting('app.tenant_id', true);
$$;

-- Core tenant-scoped tables
ALTER TABLE user_account ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_account;
CREATE POLICY tenant_isolation ON user_account
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE application ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON application;
CREATE POLICY tenant_isolation ON application
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON role;
CREATE POLICY tenant_isolation ON role
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE rbac_group ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON rbac_group;
CREATE POLICY tenant_isolation ON rbac_group
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_permission ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_permission;
CREATE POLICY tenant_isolation ON tenant_permission
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_role ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_role;
CREATE POLICY tenant_isolation ON tenant_role
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_user_roster ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_user_roster;
CREATE POLICY tenant_isolation ON tenant_user_roster
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE admin_console_access ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON admin_console_access;
CREATE POLICY tenant_isolation ON admin_console_access
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE admin_console_feature_override ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON admin_console_feature_override;
CREATE POLICY tenant_isolation ON admin_console_feature_override
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE tenant_console_role_feature ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_console_role_feature;
CREATE POLICY tenant_isolation ON tenant_console_role_feature
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE otp_challenge ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON otp_challenge;
CREATE POLICY tenant_isolation ON otp_challenge
    USING (secureone_tenant_matches(tenant_id));

-- Nullable tenant_id: visible only when bypassing or when tenant matches
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (
        secureone_rls_bypass()
        OR (tenant_id IS NOT NULL AND secureone_tenant_matches(tenant_id))
    );

ALTER TABLE login_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON login_history;
CREATE POLICY tenant_isolation ON login_history
    USING (
        secureone_rls_bypass()
        OR (tenant_id IS NOT NULL AND secureone_tenant_matches(tenant_id))
    );

-- MFA factors inherit tenant scope via user_account
ALTER TABLE mfa_factor ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mfa_factor;
CREATE POLICY tenant_isolation ON mfa_factor
    USING (
        secureone_rls_bypass()
        OR EXISTS (
            SELECT 1
            FROM user_account u
            WHERE u.id = mfa_factor.user_id
              AND u.tenant_id::text = current_setting('app.tenant_id', true)
        )
    );

-- App DB role must not bypass policies (table owner otherwise skips RLS).
ALTER TABLE user_account FORCE ROW LEVEL SECURITY;
ALTER TABLE application FORCE ROW LEVEL SECURITY;
ALTER TABLE role FORCE ROW LEVEL SECURITY;
ALTER TABLE rbac_group FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_permission FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_role FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_user_roster FORCE ROW LEVEL SECURITY;
ALTER TABLE admin_console_access FORCE ROW LEVEL SECURITY;
ALTER TABLE admin_console_feature_override FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_console_role_feature FORCE ROW LEVEL SECURITY;
ALTER TABLE otp_challenge FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
ALTER TABLE login_history FORCE ROW LEVEL SECURITY;
ALTER TABLE mfa_factor FORCE ROW LEVEL SECURITY;
```

### `V55__identity_federation.sql`

```sql
-- Identity federation (Phase 3 foundation): external IdPs + linked user identities.

CREATE TABLE identity_provider (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id  UUID REFERENCES application(id) ON DELETE CASCADE,
    alias           VARCHAR(64) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    display_name    VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT false,
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_identity_provider_tenant_alias_platform
    ON identity_provider (tenant_id, alias)
    WHERE application_id IS NULL;

CREATE UNIQUE INDEX uq_identity_provider_tenant_app_alias
    ON identity_provider (tenant_id, application_id, alias)
    WHERE application_id IS NOT NULL;

CREATE INDEX idx_identity_provider_tenant ON identity_provider (tenant_id);
CREATE INDEX idx_identity_provider_application ON identity_provider (application_id);

CREATE TABLE user_identity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    provider_id       UUID NOT NULL REFERENCES identity_provider(id) ON DELETE CASCADE,
    provider_subject  VARCHAR(512) NOT NULL,
    email             VARCHAR(320),
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    linked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_id, provider_subject)
);

CREATE INDEX idx_user_identity_user ON user_identity (user_id);
CREATE INDEX idx_user_identity_provider ON user_identity (provider_id);

-- SCIM bearer token placeholder (enabled via platform settings bootstrap / admin).
INSERT INTO platform_setting (key, value)
VALUES (
    'scim',
    '{
      "enabled": false,
      "bearerTokenEncrypted": "",
      "userNameDomain": "secureone.local"
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

-- Tenant-scoped federation tables
ALTER TABLE identity_provider ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON identity_provider;
CREATE POLICY tenant_isolation ON identity_provider
    USING (secureone_tenant_matches(tenant_id));

ALTER TABLE user_identity ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_identity;
CREATE POLICY tenant_isolation ON user_identity
    USING (
        secureone_rls_bypass()
        OR EXISTS (
            SELECT 1
            FROM user_account u
            WHERE u.id = user_identity.user_id
              AND u.tenant_id::text = current_setting('app.tenant_id', true)
        )
    );

ALTER TABLE identity_provider FORCE ROW LEVEL SECURITY;
ALTER TABLE user_identity FORCE ROW LEVEL SECURITY;
```

### `V56__scim_groups_service_accounts.sql`

```sql
-- SCIM groups + service accounts / API keys (Phase 2 M2M foundation).

CREATE TABLE scim_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    display_name    VARCHAR(255) NOT NULL,
    external_id     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_scim_group_tenant_external
    ON scim_group(tenant_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_scim_group_tenant ON scim_group(tenant_id);

CREATE TABLE scim_group_member (
    group_id    UUID NOT NULL REFERENCES scim_group(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_scim_group_member_user ON scim_group_member(user_id);

CREATE TABLE service_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_service_account_tenant_name ON service_account(tenant_id, name);
CREATE INDEX idx_service_account_tenant ON service_account(tenant_id);

CREATE TABLE api_key (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_account_id  UUID NOT NULL REFERENCES service_account(id) ON DELETE CASCADE,
    key_prefix          VARCHAR(16) NOT NULL,
    key_hash            VARCHAR(128) NOT NULL,
    label               VARCHAR(150),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_api_key_hash ON api_key(key_hash);
CREATE INDEX idx_api_key_service_account ON api_key(service_account_id);

ALTER TABLE scim_group ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON scim_group;
CREATE POLICY tenant_isolation ON scim_group
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE scim_group FORCE ROW LEVEL SECURITY;

ALTER TABLE service_account ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON service_account;
CREATE POLICY tenant_isolation ON service_account
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE service_account FORCE ROW LEVEL SECURITY;
```

### `V57__webhooks_trusted_device_iga.sql`

```sql
-- Phase 2-4 foundation: webhooks, trusted devices, IGA, organizations, tenant quotas.

CREATE TABLE webhook_endpoint (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    application_id  UUID REFERENCES application(id) ON DELETE CASCADE,
    url             VARCHAR(2048) NOT NULL,
    secret_encrypted TEXT NOT NULL,
    events          JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoint_tenant ON webhook_endpoint(tenant_id);
CREATE INDEX idx_webhook_endpoint_application ON webhook_endpoint(application_id)
    WHERE application_id IS NOT NULL;

CREATE TABLE webhook_delivery (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id     UUID NOT NULL REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    response_code   INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX idx_webhook_delivery_created ON webhook_delivery(created_at DESC);

CREATE TABLE trusted_device (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    device_token_hash   VARCHAR(128) NOT NULL,
    label               VARCHAR(150),
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_trusted_device_user_token ON trusted_device(user_id, device_token_hash);
CREATE INDEX idx_trusted_device_user ON trusted_device(user_id);

CREATE TABLE access_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    requester_user_id   UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    target_user_id      UUID REFERENCES user_account(id) ON DELETE SET NULL,
    resource_type       VARCHAR(64) NOT NULL,
    resource_id         VARCHAR(255) NOT NULL,
    reason              TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id         UUID REFERENCES user_account(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_access_request_tenant ON access_request(tenant_id);
CREATE INDEX idx_access_request_status ON access_request(tenant_id, status);

CREATE TABLE organization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES organization(id) ON DELETE SET NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_organization_tenant_slug ON organization(tenant_id, slug);
CREATE INDEX idx_organization_tenant ON organization(tenant_id);
CREATE INDEX idx_organization_parent ON organization(parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE tenant_quota (
    tenant_id           UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    max_users           INTEGER NOT NULL DEFAULT 1000,
    max_applications    INTEGER NOT NULL DEFAULT 50,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE webhook_endpoint ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON webhook_endpoint;
CREATE POLICY tenant_isolation ON webhook_endpoint
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE webhook_endpoint FORCE ROW LEVEL SECURITY;

ALTER TABLE access_request ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON access_request;
CREATE POLICY tenant_isolation ON access_request
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE access_request FORCE ROW LEVEL SECURITY;

ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON organization;
CREATE POLICY tenant_isolation ON organization
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE organization FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant_quota ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_quota;
CREATE POLICY tenant_isolation ON tenant_quota
    FOR ALL USING (secureone_tenant_matches(tenant_id));
ALTER TABLE tenant_quota FORCE ROW LEVEL SECURITY;
```

### `V58__access_request_application_id.sql`

```sql
ALTER TABLE access_request
    ADD COLUMN application_id UUID REFERENCES application(id) ON DELETE SET NULL;

ALTER TABLE access_request
    ADD COLUMN grant_type VARCHAR(64),
    ADD COLUMN granted_role_id UUID,
    ADD COLUMN granted_user_id UUID REFERENCES user_account(id) ON DELETE SET NULL;

CREATE INDEX idx_access_request_application ON access_request(application_id)
    WHERE application_id IS NOT NULL;
```

### `V59__access_request_granted_role_name.sql`

```sql
ALTER TABLE access_request
    ADD COLUMN granted_role_name VARCHAR(255);
```

### `V60__terrafarm_oauth_redirect.sql`

```sql
-- TerraFarm (Godot) OAuth PKCE callback URIs for the external test application.

UPDATE application
SET config = jsonb_set(
    config,
    '{redirectUris}',
    (
        SELECT to_jsonb(ARRAY(
            SELECT DISTINCT uri
            FROM unnest(
                COALESCE(
                    ARRAY(SELECT jsonb_array_elements_text(config->'redirectUris')),
                    ARRAY[]::text[]
                )
                || ARRAY[
                    'http://127.0.0.1:8790/oauth/callback',
                    'http://localhost:8790/oauth/callback'
                ]
            ) AS uri
        ))
    ),
    true
),
updated_at = now()
WHERE id = '22222222-2222-2222-2222-222222222299';
```

### `V61__rls_fail_closed_option.sql`

```sql
-- Opt-in fail-closed row-level security.
--
-- app.rls_enforce = 'on'  (connection default, from SECUREONE_RLS_ENFORCE)
--     Rows are visible only for the tenant in app.tenant_id, or all rows when the transaction
--     explicitly sets app.rls_bypass = 'on' (platform operators and system jobs).
--     A transaction with neither sees no tenant-scoped rows.
-- app.rls_enforce unset / 'off'
--     Legacy behaviour from V54: an empty app.tenant_id sees every row.
--
-- secureone_tenant_matches() and every policy call this function, so no policy changes are needed.
CREATE OR REPLACE FUNCTION secureone_rls_bypass() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT COALESCE(current_setting('app.rls_bypass', true), '') = 'on'
        OR (
            COALESCE(current_setting('app.rls_enforce', true), '') <> 'on'
            AND COALESCE(current_setting('app.tenant_id', true), '') = ''
        );
$$;
```

### `V62__named_platform_operators.sql`

```sql
-- Named platform operators (replaces the single shared operator account for day-to-day use).
--
-- Platform operators are ordinary user accounts in the reserved "platform" tenant, so they get
-- passwords, MFA, lockout and audit like everyone else. platform_operator records who holds the
-- platform-admin role. The configured break-glass account (SECUREONE_DEV_USER) remains for
-- emergencies only.

INSERT INTO platform.tenant (slug, name, status, settings)
VALUES ('platform', 'Platform operators', 'ACTIVE', '{"system": true, "plan": "enterprise"}'::jsonb)
ON CONFLICT (slug) DO NOTHING;

CREATE TABLE IF NOT EXISTS platform.platform_operator (
    user_id    UUID PRIMARY KEY REFERENCES platform.user_account (id) ON DELETE CASCADE,
    granted_by VARCHAR(320),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### `V63__rls_application_role.sql`

```sql
-- Row-level security only applies to roles that are neither superuser nor BYPASSRLS. The schema
-- owner used by Flyway is often both, so request-time queries switch (SET ROLE, see the datasource
-- connection-init-sql) to this role: data privileges only, policies always enforced.
--
-- Production may instead give the application its own LOGIN role that is a member of
-- secureone_app; the startup check refuses to enforce RLS on a connection that bypasses it.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'secureone_app') THEN
        CREATE ROLE secureone_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

-- Lets a non-superuser schema owner SET ROLE to it (superusers can anyway).
GRANT secureone_app TO CURRENT_USER;

-- Existing schemas: platform, public and every application schema.
DO $$
DECLARE
    s text;
BEGIN
    FOR s IN
        SELECT nspname FROM pg_namespace
        WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema'
    LOOP
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA %I TO secureone_app', s);
        EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %I TO secureone_app', s);
    END LOOP;
END
$$;

-- Objects the owner creates later (new migrations, newly provisioned application schemas).
ALTER DEFAULT PRIVILEGES GRANT USAGE ON SCHEMAS TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO secureone_app;
ALTER DEFAULT PRIVILEGES GRANT EXECUTE ON FUNCTIONS TO secureone_app;
```

### `V64__rls_functions_schema_qualified.sql`

```sql
-- The tenant policy called secureone_rls_bypass() unqualified, so it failed whenever search_path
-- did not include the platform schema (application-schema transactions switch search_path). It
-- went unnoticed because the old connection role skipped RLS (see V63). Same logic, qualified name.
CREATE OR REPLACE FUNCTION platform.secureone_tenant_matches(row_tenant_id uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT platform.secureone_rls_bypass()
        OR row_tenant_id::text = current_setting('app.tenant_id', true);
$$;
```

### `V65__remove_stale_platform_rbac_copies.sql`

```sql
-- The startup catalog repair used to run without an application's schema, so it seeded each
-- isolated application's default roles and permissions into the shared platform tables as well.
-- Isolated applications keep their RBAC in their own schema; these platform copies were never read
-- for them (tokens, console and checks use the application's schema). Remove them. Applications
-- that still use the shared tables (schema_name IS NULL) are not touched.

DELETE FROM platform.rbac_group
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);

DELETE FROM platform.role
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);

DELETE FROM platform.permission
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);
```

### `V66__application_logs_single_table.sql`

```sql
-- Application logs are written by a background appender into platform.application_log, but isolating
-- an application moved its existing rows into <app schema>.application_log, and app-scoped reads
-- (search_path set to the app schema) read that copy. New events never appeared in an application's
-- log view. Logs now live only in the platform table, filtered by application_id like audit_log and
-- login_history. Fold the moved rows back; the per-app tables are left empty.

DO $$
DECLARE
    s TEXT;
BEGIN
    FOR s IN
        SELECT DISTINCT schema_name FROM platform.application
        WHERE schema_name IS NOT NULL AND schema_name <> 'platform'
    LOOP
        IF to_regclass(format('%I.application_log', s)) IS NOT NULL THEN
            EXECUTE format(
                'INSERT INTO platform.application_log (id, created_at, level, logger, message, session_id, request_id,'
                    || ' principal, tenant_id, application_id, ip, user_agent, metadata)'
                    || ' SELECT id, created_at, level, logger, message, session_id, request_id,'
                    || ' principal, tenant_id, application_id, ip, user_agent, metadata FROM %I.application_log'
                    || ' ON CONFLICT (id) DO NOTHING',
                s);
            EXECUTE format('DELETE FROM %I.application_log', s);
        END IF;
    END LOOP;
END $$;
```

### `V67__oauth_client_subject_type.sql`

```sql
-- Which value an OAuth client receives as the token subject ("sub").
--   legacy  : tenant-slug:email (changes when the user changes email)
--   user_id : the account UUID (stable; what new integrations should key users on)
-- Existing clients keep legacy so integrations that stored "sub" keep working; new clients default to
-- user_id in the application. Switch a client explicitly once its app keys users on user_id.
ALTER TABLE platform.oauth_client
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(16) NOT NULL DEFAULT 'legacy';

ALTER TABLE platform.oauth_client
    ADD CONSTRAINT oauth_client_subject_type_check CHECK (subject_type IN ('legacy', 'user_id'));
```

### `R__z_repair_catalog.sql`

```sql
-- Idempotent catalog repair (repeatable). Safe after TRUNCATE on catalog tables.
-- Re-run:  .\scripts\reseed-catalog.ps1   OR   flyway migrate (when this file changes)

-- â”€â”€ Platform settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO platform_setting (key, value)
VALUES
    ('notifications', '{"emailEnabled":true,"auditAlertsEnabled":true,"securityAlertsEnabled":true,"adminRecipients":[]}'::jsonb),
    ('email', '{"fromName":"SecureOne","fromAddress":"noreply@secureone.local","replyTo":"support@secureone.local"}'::jsonb),
    ('smtp', '{"host":"","port":465,"security":"ssl","username":"","authEnabled":true}'::jsonb),
    ('email_templates', '{}'::jsonb),
    ('appearance', '{}'::jsonb),
    ('app_settings_exposure', '{
      "notifications": true, "email": true, "auth-methods": true, "password-policy": true,
      "feature-flags": true, "appearance": false, "user-directory": true,
      "public-manifest": true, "token-policy": true
    }'::jsonb)
ON CONFLICT (key) DO NOTHING;

INSERT INTO platform_setting (key, value)
VALUES
    ('auth_methods', '[
      {"id":"m_password","name":"Password","description":"Username + password","enabled":true,"category":"primary","implemented":true},
      {"id":"m_passkey","name":"Passkeys (WebAuthn)","description":"Phishing-resistant sign-in","enabled":true,"category":"primary","implemented":false},
      {"id":"m_magic","name":"Magic Link","description":"Email passwordless login","enabled":true,"category":"primary","implemented":true},
      {"id":"m_totp","name":"TOTP Authenticator","description":"Authenticator app codes","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_sms","name":"SMS OTP","description":"SMS one-time codes","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_email_otp","name":"Email OTP","description":"Email one-time codes at login","enabled":true,"category":"mfa","implemented":false},
      {"id":"m_push","name":"Push Notification","description":"Approve on device","enabled":false,"category":"mfa","implemented":false},
      {"id":"m_google","name":"Google","description":"Google OIDC","enabled":true,"category":"federation","implemented":false},
      {"id":"m_github","name":"GitHub","description":"GitHub OAuth","enabled":true,"category":"federation","implemented":false},
      {"id":"m_saml","name":"SAML 2.0","description":"Enterprise SSO","enabled":false,"category":"federation","implemented":false},
      {"id":"m_oidc","name":"External OIDC","description":"OIDC federation","enabled":false,"category":"federation","implemented":false}
    ]'::jsonb),
    ('password_policy', '{
      "minLength": 12, "requireUppercase": true, "requireNumber": true, "requireSymbol": true,
      "expiryDays": 0, "historyCount": 5, "hashAlgorithm": "bcrypt"
    }'::jsonb),
    ('feature_flags', '[
      {"key":"self_service_recovery","name":"Self-service Recovery","description":"Application-scoped forgot-password","enabled":true,"rollout":100,"category":"identity"},
      {"key":"self_registration","name":"Self Registration","description":"Public user signup","enabled":false,"rollout":0,"category":"identity"},
      {"key":"ldap","name":"LDAP / AD","description":"LDAP user import","enabled":false,"rollout":0,"category":"identity"},
      {"key":"notification_email_test_ui","name":"Email test console","description":"SMTP test panel under Notifications","enabled":true,"rollout":100,"category":"notifications"}
    ]'::jsonb),
    ('user_directory', '{
      "importEnabled": false, "exportEnabled": false,
      "sources": {"csv": {"enabled": true}, "excel": {"enabled": true}, "ldap": {"enabled": false}},
      "ldap": {"host": "", "port": 389, "baseDn": "", "bindDn": "", "bindPassword": "", "userFilter": "(mail={0})", "useTls": true}
    }'::jsonb),
    ('public_manifest_defaults', '{
      "enabled": false,
      "sections": {"application": true, "authMethods": true, "featureFlags": true, "passwordPolicy": true, "appearance": false},
      "authMethodsOnlyEnabled": true
    }'::jsonb),
    ('token_policy', '{
      "accessTokenTtlSeconds": 3600, "refreshTokenTtlSeconds": 604800, "authorizationCodeTtlSeconds": 300,
      "idTokenTtlSeconds": 3600, "clientCredentialsTtlSeconds": 3600, "deviceCodeTtlSeconds": 600,
      "refreshTokensEnabled": true, "reuseRefreshTokens": false, "rotateRefreshTokens": true,
      "refreshTokenReuseDetection": true
    }'::jsonb)
ON CONFLICT (key) DO NOTHING;

-- â”€â”€ Tenant governance RBAC (per existing tenant) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO tenant_permission (id, tenant_id, key, description)
SELECT gen_random_uuid(), t.id, e.key, e.description
FROM tenant t
CROSS JOIN (
    VALUES
        ('tenant:read', 'View tenant metadata and roster'),
        ('tenant:manage', 'Edit tenant settings and roster'),
        ('operator:read', 'View tenant operators and console access'),
        ('operator:manage', 'Grant or revoke operator console access'),
        ('application:access', 'Assign users to applications'),
        ('application:manage', 'Create and configure applications'),
        ('console:users', 'Admin console â€” Users section'),
        ('console:roles', 'Admin console â€” Roles section'),
        ('console:groups', 'Admin console â€” Groups section'),
        ('console:permissions', 'Admin console â€” Permissions section'),
        ('console:settings', 'Admin console â€” Settings section'),
        ('console:audit', 'Admin console â€” Audit log'),
        ('console:logs', 'Admin console â€” Application logs'),
        ('console:sessions', 'Admin console â€” Sessions')
) AS e(key, description)
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_permission p WHERE p.tenant_id = t.id AND p.key = e.key
);

INSERT INTO tenant_role (id, tenant_id, name, description, is_system)
SELECT gen_random_uuid(), t.id, r.name, r.description, true
FROM tenant t
CROSS JOIN (
    VALUES
        ('Tenant Administrator', 'Full tenant governance â€” operators, applications, and console features'),
        ('Application Operator', 'Manage users, groups, and settings for assigned applications'),
        ('Tenant Auditor', 'Read-only access to tenant roster, audit, and logs')
) AS r(name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_role tr WHERE tr.tenant_id = t.id AND tr.name = r.name
);

INSERT INTO tenant_role_permission (role_id, permission_id)
SELECT tr.id, tp.id
FROM tenant_role tr
JOIN tenant_permission tp ON tp.tenant_id = tr.tenant_id
WHERE (
    (tr.name = 'Tenant Administrator' AND tp.key IN (
        'tenant:read', 'tenant:manage', 'operator:read', 'operator:manage',
        'application:access', 'application:manage',
        'console:users', 'console:roles', 'console:groups', 'console:permissions', 'console:settings',
        'console:audit', 'console:logs', 'console:sessions'))
    OR (tr.name = 'Application Operator' AND tp.key IN (
        'tenant:read', 'operator:read', 'application:access',
        'console:users', 'console:roles', 'console:groups', 'console:settings', 'console:audit', 'console:sessions'))
    OR (tr.name = 'Tenant Auditor' AND tp.key IN (
        'tenant:read', 'operator:read', 'console:audit', 'console:logs', 'console:sessions'))
)
AND NOT EXISTS (
    SELECT 1 FROM tenant_role_permission rp
    WHERE rp.role_id = tr.id AND rp.permission_id = tp.id
);

-- â”€â”€ Application permission catalog (per existing OAuth client) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO permission (id, application_id, key, description)
SELECT gen_random_uuid(), a.id, d.key, d.description
FROM application a
CROSS JOIN (
    VALUES
        ('user:read', 'View users and profiles'),
        ('user:write', 'Create and edit users'),
        ('user:delete', 'Delete users'),
        ('role:read', 'View roles and permissions'),
        ('role:write', 'Manage roles and assignments'),
        ('app:read', 'View applications'),
        ('app:write', 'Manage OAuth clients'),
        ('audit:read', 'View audit logs'),
        ('logs:read', 'View application logs'),
        ('settings:write', 'Change application settings'),
        ('session:read', 'View active sessions')
) AS d(key, description)
WHERE NOT EXISTS (
    SELECT 1 FROM permission p WHERE p.application_id = a.id AND p.key = d.key
);

-- â”€â”€ Application system roles (per existing OAuth client) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Tenant Admin',
       'SecureOne tenant operator for this application. Assign to users who manage the app and can be imported to the tenant roster.',
       true, false, false
FROM application a
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Tenant Admin');

UPDATE role
SET is_system = true,
    description = 'SecureOne tenant operator for this application. Assign to users who manage the app and can be imported to the tenant roster.'
WHERE name = 'Tenant Admin';

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Member', 'Standard end-user access', true, true, false
FROM application a
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Member');

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Application Admin',
       'Manage users and roles for this application in SecureOne Admin', true, false, false
FROM application a
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Application Admin');

INSERT INTO role (id, tenant_id, application_id, name, description, is_system, is_default, is_composite)
SELECT gen_random_uuid(), a.tenant_id, a.id, 'Security Auditor', 'Read-only access to audit and identity data', true, false, false
FROM application a
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.application_id = a.id AND r.name = 'Security Auditor');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.application_id = r.application_id
WHERE (
    (r.name = 'Tenant Admin' AND p.key IN (
        'user:read', 'user:write', 'user:delete', 'role:read', 'role:write',
        'app:read', 'app:write', 'settings:write', 'audit:read', 'session:read'))
    OR (r.name = 'Member' AND p.key = 'user:read')
    OR (r.name = 'Application Admin' AND p.key IN (
        'user:read', 'user:write', 'role:read', 'app:read', 'audit:read', 'session:read', 'logs:read'))
    OR (r.name = 'Security Auditor' AND p.key IN ('audit:read', 'user:read', 'role:read', 'session:read'))
)
AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- â”€â”€ Console operator defaults: grant groups where roles already enabled â”€â”€â”€â”€â”€â”€â”€
INSERT INTO tenant_console_role_feature (tenant_id, role_key, feature_key)
SELECT DISTINCT tcrf.tenant_id, tcrf.role_key, 'groups'
FROM tenant_console_role_feature tcrf
WHERE tcrf.feature_key = 'roles'
AND tcrf.role_key IN ('APPLICATION_ADMIN', 'TENANT_ADMIN', 'TENANT_SUPER_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM tenant_console_role_feature existing
    WHERE existing.tenant_id = tcrf.tenant_id
      AND existing.role_key = tcrf.role_key
      AND existing.feature_key = 'groups'
);

```

