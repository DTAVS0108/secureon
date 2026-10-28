-- Idempotent catalog repair (repeatable). Safe after TRUNCATE on catalog tables.
-- Flyway re-runs it whenever this file changes.

-- ── Platform settings ────────────────────────────────────────────────────────
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
      "public-manifest": true, "token-policy": true, "verification-policy": true, "sms": true
    }'::jsonb)
ON CONFLICT (key) DO NOTHING;

INSERT INTO platform_setting (key, value)
VALUES
    ('auth_methods', '[
      {"id":"m_password","name":"Password","description":"Username + password","enabled":true,"category":"primary","implemented":true},
      {"id":"m_passkey","name":"Passkeys (WebAuthn)","description":"Phishing-resistant sign-in","enabled":true,"category":"primary","implemented":false},
      {"id":"m_magic","name":"Magic Link","description":"Email passwordless login","enabled":true,"category":"primary","implemented":true},
      {"id":"m_sms","name":"SMS OTP","description":"SMS one-time codes","enabled":false,"category":"mfa","implemented":true},
      {"id":"m_email_otp","name":"Email OTP","description":"Email one-time codes at login","enabled":true,"category":"mfa","implemented":true},
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
      {"key":"self_service_recovery","name":"Self-service Recovery","description":"Application-scoped forgot-password (link or OTP per verification policy)","enabled":true,"rollout":100,"category":"identity"},
      {"key":"recovery_otp","name":"Recovery OTP","description":"Password reset via email/SMS OTP codes","enabled":true,"rollout":100,"category":"identity"},
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

-- ── Tenant governance RBAC (per existing tenant) ─────────────────────────────
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
        ('console:users', 'Admin console — Users section'),
        ('console:roles', 'Admin console — Roles section'),
        ('console:groups', 'Admin console — Groups section'),
        ('console:permissions', 'Admin console — Permissions section'),
        ('console:settings', 'Admin console — Settings section'),
        ('console:audit', 'Admin console — Audit log'),
        ('console:logs', 'Admin console — Application logs'),
        ('console:sessions', 'Admin console — Sessions')
) AS e(key, description)
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_permission p WHERE p.tenant_id = t.id AND p.key = e.key
);

INSERT INTO tenant_role (id, tenant_id, name, description, is_system)
SELECT gen_random_uuid(), t.id, r.name, r.description, true
FROM tenant t
CROSS JOIN (
    VALUES
        ('Tenant Administrator', 'Full tenant governance — operators, applications, and console features'),
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

-- ── Application permission catalog (per existing OAuth client) ─────────────────
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

-- ── Application system roles (per existing OAuth client) ───────────────────────
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

-- ── Console operator defaults: grant groups where roles already enabled ───────
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
