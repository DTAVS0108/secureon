-- SecureOne platform schema (baseline).
-- Creates every platform table, function, trigger and row-level security policy, plus the built-in
-- reference rows: the system "platform" tenant, its operator roles and permissions, and default
-- platform settings. No tenants, users or applications: the setup wizard creates the first operator.
-- Later changes go in V2__..., V3__... as usual.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE FUNCTION platform.audit_log_append_only() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Deliberate maintenance only: SET LOCAL secureone.audit_maintenance = 'on' in that transaction.
    IF coalesce(current_setting('secureone.audit_maintenance', true), '') = 'on' THEN
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'The audit log cannot be changed' USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF OLD.created_at > now() - interval '30 days' THEN
        RAISE EXCEPTION 'Audit entries are kept for at least 30 days' USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN OLD;
END;
$$;

CREATE FUNCTION platform.secureone_rls_bypass() RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT COALESCE(current_setting('app.rls_bypass', true), '') = 'on'
        OR (
            COALESCE(current_setting('app.rls_enforce', true), '') <> 'on'
            AND COALESCE(current_setting('app.tenant_id', true), '') = ''
        );
$$;

CREATE FUNCTION platform.secureone_tenant_matches(row_tenant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT platform.secureone_rls_bypass()
        OR row_tenant_id::text = current_setting('app.tenant_id', true);
$$;

CREATE TABLE platform.access_request (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    requester_user_id uuid NOT NULL,
    target_user_id uuid,
    resource_type character varying(64) NOT NULL,
    resource_id character varying(255) NOT NULL,
    reason text,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewer_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    resolved_at timestamp with time zone,
    application_id uuid,
    grant_type character varying(64),
    granted_role_id uuid,
    granted_user_id uuid,
    granted_role_name character varying(255)
);

ALTER TABLE ONLY platform.access_request FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.access_review (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    completed_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_by character varying(320) NOT NULL,
    entries_reviewed integer DEFAULT 0 NOT NULL,
    flagged_count integer DEFAULT 0 NOT NULL,
    revoked_count integer DEFAULT 0 NOT NULL,
    note character varying(500)
);

CREATE TABLE platform.account_deletion_request (
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    requested_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.admin_console_access (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid,
    role_type character varying(32) NOT NULL,
    granted_by uuid,
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    CONSTRAINT admin_console_access_role_type_chk CHECK (((role_type)::text = ANY ((ARRAY['APPLICATION_ADMIN'::character varying, 'TENANT_ADMIN'::character varying, 'TENANT_SUPER_ADMIN'::character varying])::text[]))),
    CONSTRAINT admin_console_access_scope_chk CHECK (((((role_type)::text = 'TENANT_SUPER_ADMIN'::text) AND (application_id IS NULL)) OR (((role_type)::text = ANY ((ARRAY['APPLICATION_ADMIN'::character varying, 'TENANT_ADMIN'::character varying])::text[])) AND (application_id IS NOT NULL))))
);

ALTER TABLE ONLY platform.admin_console_access FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.admin_console_feature_override (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    feature_key character varying(64) NOT NULL,
    effect character varying(8) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT admin_console_feature_override_effect_chk CHECK (((effect)::text = ANY ((ARRAY['GRANT'::character varying, 'DENY'::character varying])::text[])))
);

ALTER TABLE ONLY platform.admin_console_feature_override FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.api_key (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    service_account_id uuid NOT NULL,
    key_prefix character varying(16) NOT NULL,
    key_hash character varying(128) NOT NULL,
    label character varying(150),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    last_used_at timestamp with time zone,
    revoked_at timestamp with time zone
);

CREATE TABLE platform.application (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    slug character varying(100) NOT NULL,
    description character varying(1000),
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    schema_name character varying(63)
);

ALTER TABLE ONLY platform.application FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.application_access_removal (
    application_id uuid NOT NULL,
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    removed_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.application_api (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid NOT NULL,
    name character varying(200) NOT NULL,
    identifier character varying(512) NOT NULL,
    scopes jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.application_client_registration (
    application_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    mode character varying(16) DEFAULT 'off'::character varying NOT NULL,
    allowed_scopes jsonb DEFAULT '["openid", "profile", "email"]'::jsonb NOT NULL,
    allowed_grant_types jsonb DEFAULT '["authorization_code", "refresh_token"]'::jsonb NOT NULL,
    trusted_hosts jsonb DEFAULT '[]'::jsonb NOT NULL,
    max_clients integer DEFAULT 100 NOT NULL,
    unused_days integer DEFAULT 30 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.application_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    level character varying(16) NOT NULL,
    logger character varying(256) NOT NULL,
    message text NOT NULL,
    session_id character varying(128),
    request_id character varying(64),
    principal character varying(320),
    tenant_id uuid,
    application_id uuid,
    ip character varying(64),
    user_agent character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);

CREATE TABLE platform.application_schema (
    application_id uuid NOT NULL,
    schema_name character varying(63) NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    flyway_version character varying(64),
    provisioned_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.application_setting (
    application_id uuid NOT NULL,
    setting_key character varying(150) NOT NULL,
    value jsonb DEFAULT '{}'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    actor_type character varying(32) NOT NULL,
    actor_id uuid,
    action character varying(150) NOT NULL,
    target_type character varying(100),
    target_id uuid,
    ip character varying(64),
    user_agent character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    application_id uuid
);

ALTER TABLE ONLY platform.audit_log FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.change_request (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    action character varying(64) NOT NULL,
    tenant_id uuid,
    application_id uuid,
    target_id uuid NOT NULL,
    target_label character varying(255) NOT NULL,
    reason character varying(500),
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    requested_by character varying(320) NOT NULL,
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    decided_by character varying(320),
    decided_at timestamp with time zone,
    decision_note character varying(500),
    payload jsonb,
    details character varying(1000),
    CONSTRAINT change_request_status_chk CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE platform.client_registration_token (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    label character varying(200),
    max_uses integer NOT NULL,
    uses integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.email_token (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    type character varying(32) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.identity_provider (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid,
    alias character varying(64) NOT NULL,
    type character varying(32) NOT NULL,
    display_name character varying(255),
    enabled boolean DEFAULT false NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.identity_provider FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.login_history (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    user_id uuid,
    result character varying(32) NOT NULL,
    ip character varying(64),
    device character varying(255),
    geo jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    application_id uuid,
    session_id character varying(128)
);

ALTER TABLE ONLY platform.login_history FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.mfa_factor (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    type character varying(32) NOT NULL,
    label character varying(255),
    secret_encrypted bytea,
    verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);

ALTER TABLE ONLY platform.mfa_factor FORCE ROW LEVEL SECURITY;

COMMENT ON COLUMN platform.mfa_factor.secret_encrypted IS 'AES-GCM encrypted TOTP seed or recovery payload';

COMMENT ON COLUMN platform.mfa_factor.metadata IS 'Recovery code hashes, WebAuthn credential fields (future)';

CREATE TABLE platform.oauth2_authorization (
    id character varying(100) NOT NULL,
    registered_client_id character varying(100) NOT NULL,
    principal_name character varying(200) NOT NULL,
    authorization_grant_type character varying(100) NOT NULL,
    authorized_scopes character varying(1000) DEFAULT NULL::character varying,
    attributes text,
    state character varying(500) DEFAULT NULL::character varying,
    authorization_code_value text,
    authorization_code_issued_at timestamp without time zone,
    authorization_code_expires_at timestamp without time zone,
    authorization_code_metadata text,
    access_token_value text,
    access_token_issued_at timestamp without time zone,
    access_token_expires_at timestamp without time zone,
    access_token_metadata text,
    access_token_type character varying(100) DEFAULT NULL::character varying,
    access_token_scopes character varying(1000) DEFAULT NULL::character varying,
    oidc_id_token_value text,
    oidc_id_token_issued_at timestamp without time zone,
    oidc_id_token_expires_at timestamp without time zone,
    oidc_id_token_metadata text,
    refresh_token_value text,
    refresh_token_issued_at timestamp without time zone,
    refresh_token_expires_at timestamp without time zone,
    refresh_token_metadata text,
    user_code_value text,
    user_code_issued_at timestamp without time zone,
    user_code_expires_at timestamp without time zone,
    user_code_metadata text,
    device_code_value text,
    device_code_issued_at timestamp without time zone,
    device_code_expires_at timestamp without time zone,
    device_code_metadata text
);

CREATE TABLE platform.oauth2_authorization_consent (
    registered_client_id character varying(100) NOT NULL,
    principal_name character varying(200) NOT NULL,
    authorities character varying(1000) NOT NULL
);

CREATE TABLE platform.oauth_client (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    client_id character varying(150) NOT NULL,
    client_secret character varying(500),
    client_name character varying(255) NOT NULL,
    type character varying(32) DEFAULT 'web'::character varying NOT NULL,
    redirect_uris jsonb DEFAULT '[]'::jsonb NOT NULL,
    post_logout_redirect_uris jsonb DEFAULT '[]'::jsonb NOT NULL,
    grant_types jsonb DEFAULT '[]'::jsonb NOT NULL,
    scopes jsonb DEFAULT '[]'::jsonb NOT NULL,
    token_endpoint_auth_method character varying(64) DEFAULT 'client_secret_basic'::character varying NOT NULL,
    require_pkce boolean DEFAULT true NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    subject_type character varying(16) DEFAULT 'legacy'::character varying NOT NULL,
    setup_framework character varying(64),
    backchannel_logout_uri character varying(1024),
    consent_required boolean DEFAULT false NOT NULL,
    previous_client_secret text,
    previous_secret_expires_at timestamp with time zone,
    jwks_uri character varying(1024),
    jwks jsonb,
    dynamically_registered boolean DEFAULT false NOT NULL,
    registration_token_hash character varying(64),
    registration_metadata jsonb,
    CONSTRAINT oauth_client_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['legacy'::character varying, 'user_id'::character varying])::text[])))
);

CREATE TABLE platform.otp_challenge (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    tenant_id uuid NOT NULL,
    application_id uuid,
    purpose character varying(32) NOT NULL,
    channel character varying(16) NOT NULL,
    destination character varying(255) NOT NULL,
    code_hash character varying(64) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    ip_address character varying(45),
    user_agent text
);

ALTER TABLE ONLY platform.otp_challenge FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.permission (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    key character varying(150) NOT NULL,
    description character varying(500)
);

CREATE TABLE platform.platform_operator (
    user_id uuid NOT NULL,
    granted_by character varying(320),
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    role character varying(32) DEFAULT 'SUPER_ADMIN'::character varying NOT NULL,
    CONSTRAINT platform_operator_role_chk CHECK (((role)::text = ANY ((ARRAY['SUPER_ADMIN'::character varying, 'READ_ONLY'::character varying])::text[])))
);

CREATE TABLE platform.platform_setting (
    key character varying(150) NOT NULL,
    value jsonb DEFAULT '{}'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.rbac_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.rbac_group FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.rbac_group_member (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    added_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.rbac_group_role (
    group_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE platform.role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(500),
    is_default boolean DEFAULT false NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    is_composite boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY platform.role FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.role_composite (
    parent_role_id uuid NOT NULL,
    child_role_id uuid NOT NULL,
    CONSTRAINT role_composite_check CHECK ((parent_role_id <> child_role_id))
);

CREATE TABLE platform.role_permission (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE platform.saml_idp_credential (
    tenant_id uuid NOT NULL,
    private_key_encrypted text NOT NULL,
    certificate_pem text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.saml_service_provider (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid NOT NULL,
    name character varying(200) NOT NULL,
    entity_id character varying(1024) NOT NULL,
    acs_url character varying(1024) NOT NULL,
    slo_url character varying(1024),
    name_id_format character varying(16) DEFAULT 'email'::character varying NOT NULL,
    sign_response boolean DEFAULT true NOT NULL,
    sign_assertion boolean DEFAULT true NOT NULL,
    sp_certificate text,
    require_signed_requests boolean DEFAULT false NOT NULL,
    attributes jsonb DEFAULT '[]'::jsonb NOT NULL,
    assertion_lifetime_seconds integer DEFAULT 300 NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.scim_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    external_id character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.scim_group FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.scim_group_member (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.service_account (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.service_account FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.tenant (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    settings jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    issuer_mode character varying(16) DEFAULT 'shared'::character varying NOT NULL
);

CREATE TABLE platform.tenant_console_role_feature (
    tenant_id uuid NOT NULL,
    role_key character varying(80) NOT NULL,
    feature_key character varying(80) NOT NULL
);

ALTER TABLE ONLY platform.tenant_console_role_feature FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.tenant_domain (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    domain character varying(253) NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    verification_token character varying(64) NOT NULL,
    verified_at timestamp with time zone,
    last_checked_at timestamp with time zone,
    last_error character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.tenant_permission (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    key character varying(150) NOT NULL,
    description character varying(500)
);

ALTER TABLE ONLY platform.tenant_permission FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.tenant_quota (
    tenant_id uuid NOT NULL,
    max_users integer DEFAULT 1000 NOT NULL,
    max_applications integer DEFAULT 50 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.tenant_quota FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.tenant_role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(500),
    is_system boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY platform.tenant_role FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.tenant_role_application (
    role_id uuid NOT NULL,
    application_id uuid NOT NULL
);

CREATE TABLE platform.tenant_role_permission (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE platform.tenant_setting (
    tenant_id uuid NOT NULL,
    setting_key character varying(150) NOT NULL,
    value jsonb DEFAULT '{}'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.tenant_user_roster (
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    added_at timestamp with time zone DEFAULT now() NOT NULL,
    source character varying(32) DEFAULT 'direct'::character varying NOT NULL,
    source_application_id uuid,
    added_by uuid
);

ALTER TABLE ONLY platform.tenant_user_roster FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.trusted_device (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    device_token_hash character varying(128) NOT NULL,
    label character varying(150),
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone,
    application_id uuid
);

CREATE TABLE platform.user_account (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    email character varying(320) NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    username character varying(150),
    phone character varying(32),
    phone_verified boolean DEFAULT false NOT NULL,
    display_name character varying(255),
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    type character varying(32) DEFAULT 'USER'::character varying NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    last_login_at timestamp with time zone,
    attributes jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lockout_level integer DEFAULT 0 NOT NULL,
    given_name character varying(150),
    family_name character varying(150)
);

ALTER TABLE ONLY platform.user_account FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.user_application (
    user_id uuid NOT NULL,
    application_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.user_credential (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    password_hash character varying(255) NOT NULL,
    algorithm character varying(64) DEFAULT 'argon2id'::character varying NOT NULL,
    params jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_current boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    expiry_warning_sent_at timestamp with time zone,
    expiry_expired_notice_sent_at timestamp with time zone,
    must_change boolean DEFAULT false NOT NULL
);

CREATE TABLE platform.user_identity (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    provider_subject character varying(512) NOT NULL,
    email character varying(320),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    linked_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.user_identity FORCE ROW LEVEL SECURITY;

CREATE TABLE platform.user_role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    role_id uuid NOT NULL,
    granted_by uuid,
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone
);

CREATE TABLE platform.user_tenant_role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    granted_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE platform.user_two_step_state (
    user_id uuid NOT NULL,
    first_prompted_at timestamp with time zone,
    next_prompt_at timestamp with time zone,
    skips_used integer DEFAULT 0 NOT NULL,
    deadline timestamp with time zone,
    opted_out boolean DEFAULT false NOT NULL,
    exempt_until timestamp with time zone,
    exempt_reason character varying(500),
    exempted_by character varying(320),
    extended_by character varying(320),
    reminders_sent character varying(100) DEFAULT ''::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    application_id uuid DEFAULT '00000000-0000-0000-0000-000000000000'::uuid NOT NULL
);

CREATE TABLE platform.webhook_delivery (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    endpoint_id uuid NOT NULL,
    event_type character varying(128) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    response_code integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_error character varying(500),
    delivered_at timestamp with time zone
);

CREATE TABLE platform.webhook_endpoint (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    application_id uuid,
    url character varying(2048) NOT NULL,
    secret_encrypted text NOT NULL,
    events jsonb DEFAULT '[]'::jsonb NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    previous_secret_encrypted text,
    previous_secret_expires_at timestamp with time zone
);

ALTER TABLE ONLY platform.webhook_endpoint FORCE ROW LEVEL SECURITY;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.access_review
    ADD CONSTRAINT access_review_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.account_deletion_request
    ADD CONSTRAINT account_deletion_request_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY platform.admin_console_access
    ADD CONSTRAINT admin_console_access_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.admin_console_feature_override
    ADD CONSTRAINT admin_console_feature_override_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.api_key
    ADD CONSTRAINT api_key_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.application_access_removal
    ADD CONSTRAINT application_access_removal_pkey PRIMARY KEY (application_id, user_id);

ALTER TABLE ONLY platform.application_api
    ADD CONSTRAINT application_api_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.application_client_registration
    ADD CONSTRAINT application_client_registration_pkey PRIMARY KEY (application_id);

ALTER TABLE ONLY platform.application_log
    ADD CONSTRAINT application_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.application
    ADD CONSTRAINT application_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.application_schema
    ADD CONSTRAINT application_schema_pkey PRIMARY KEY (application_id);

ALTER TABLE ONLY platform.application_schema
    ADD CONSTRAINT application_schema_schema_name_key UNIQUE (schema_name);

ALTER TABLE ONLY platform.application_setting
    ADD CONSTRAINT application_setting_pkey PRIMARY KEY (application_id, setting_key);

ALTER TABLE ONLY platform.application
    ADD CONSTRAINT application_tenant_id_slug_key UNIQUE (tenant_id, slug);

ALTER TABLE ONLY platform.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.change_request
    ADD CONSTRAINT change_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.client_registration_token
    ADD CONSTRAINT client_registration_token_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.client_registration_token
    ADD CONSTRAINT client_registration_token_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY platform.email_token
    ADD CONSTRAINT email_token_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.identity_provider
    ADD CONSTRAINT identity_provider_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.login_history
    ADD CONSTRAINT login_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.mfa_factor
    ADD CONSTRAINT mfa_factor_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.oauth2_authorization_consent
    ADD CONSTRAINT oauth2_authorization_consent_pkey PRIMARY KEY (registered_client_id, principal_name);

ALTER TABLE ONLY platform.oauth2_authorization
    ADD CONSTRAINT oauth2_authorization_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.oauth_client
    ADD CONSTRAINT oauth_client_client_id_key UNIQUE (client_id);

ALTER TABLE ONLY platform.oauth_client
    ADD CONSTRAINT oauth_client_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.otp_challenge
    ADD CONSTRAINT otp_challenge_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.permission
    ADD CONSTRAINT permission_application_id_key_key UNIQUE (application_id, key);

ALTER TABLE ONLY platform.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.platform_operator
    ADD CONSTRAINT platform_operator_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY platform.platform_setting
    ADD CONSTRAINT platform_setting_pkey PRIMARY KEY (key);

ALTER TABLE ONLY platform.rbac_group
    ADD CONSTRAINT rbac_group_application_id_name_key UNIQUE (application_id, name);

ALTER TABLE ONLY platform.rbac_group_member
    ADD CONSTRAINT rbac_group_member_pkey PRIMARY KEY (group_id, user_id);

ALTER TABLE ONLY platform.rbac_group
    ADD CONSTRAINT rbac_group_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.rbac_group_role
    ADD CONSTRAINT rbac_group_role_pkey PRIMARY KEY (group_id, role_id);

ALTER TABLE ONLY platform.role
    ADD CONSTRAINT role_application_id_name_key UNIQUE (application_id, name);

ALTER TABLE ONLY platform.role_composite
    ADD CONSTRAINT role_composite_pkey PRIMARY KEY (parent_role_id, child_role_id);

ALTER TABLE ONLY platform.role_permission
    ADD CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY platform.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.saml_idp_credential
    ADD CONSTRAINT saml_idp_credential_pkey PRIMARY KEY (tenant_id);

ALTER TABLE ONLY platform.saml_service_provider
    ADD CONSTRAINT saml_service_provider_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.scim_group_member
    ADD CONSTRAINT scim_group_member_pkey PRIMARY KEY (group_id, user_id);

ALTER TABLE ONLY platform.scim_group
    ADD CONSTRAINT scim_group_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.service_account
    ADD CONSTRAINT service_account_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.tenant_console_role_feature
    ADD CONSTRAINT tenant_console_role_feature_pkey PRIMARY KEY (tenant_id, role_key, feature_key);

ALTER TABLE ONLY platform.tenant_domain
    ADD CONSTRAINT tenant_domain_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.tenant_permission
    ADD CONSTRAINT tenant_permission_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.tenant_permission
    ADD CONSTRAINT tenant_permission_tenant_id_key_key UNIQUE (tenant_id, key);

ALTER TABLE ONLY platform.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.tenant_quota
    ADD CONSTRAINT tenant_quota_pkey PRIMARY KEY (tenant_id);

ALTER TABLE ONLY platform.tenant_role_application
    ADD CONSTRAINT tenant_role_application_pkey PRIMARY KEY (role_id, application_id);

ALTER TABLE ONLY platform.tenant_role_permission
    ADD CONSTRAINT tenant_role_permission_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY platform.tenant_role
    ADD CONSTRAINT tenant_role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.tenant_role
    ADD CONSTRAINT tenant_role_tenant_id_name_key UNIQUE (tenant_id, name);

ALTER TABLE ONLY platform.tenant_setting
    ADD CONSTRAINT tenant_setting_pkey PRIMARY KEY (tenant_id, setting_key);

ALTER TABLE ONLY platform.tenant
    ADD CONSTRAINT tenant_slug_key UNIQUE (slug);

ALTER TABLE ONLY platform.tenant_user_roster
    ADD CONSTRAINT tenant_user_roster_pkey PRIMARY KEY (tenant_id, user_id);

ALTER TABLE ONLY platform.trusted_device
    ADD CONSTRAINT trusted_device_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.admin_console_feature_override
    ADD CONSTRAINT uq_admin_console_feature_override UNIQUE (user_id, tenant_id, feature_key);

ALTER TABLE ONLY platform.user_account
    ADD CONSTRAINT user_account_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.user_account
    ADD CONSTRAINT user_account_tenant_id_email_key UNIQUE (tenant_id, email);

ALTER TABLE ONLY platform.user_application
    ADD CONSTRAINT user_application_pkey PRIMARY KEY (user_id, application_id);

ALTER TABLE ONLY platform.user_credential
    ADD CONSTRAINT user_credential_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.user_identity
    ADD CONSTRAINT user_identity_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.user_identity
    ADD CONSTRAINT user_identity_provider_id_provider_subject_key UNIQUE (provider_id, provider_subject);

ALTER TABLE ONLY platform.user_role
    ADD CONSTRAINT user_role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.user_tenant_role
    ADD CONSTRAINT user_tenant_role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.user_tenant_role
    ADD CONSTRAINT user_tenant_role_user_id_role_id_key UNIQUE (user_id, role_id);

ALTER TABLE ONLY platform.user_two_step_state
    ADD CONSTRAINT user_two_step_state_pkey PRIMARY KEY (user_id, application_id);

ALTER TABLE ONLY platform.webhook_delivery
    ADD CONSTRAINT webhook_delivery_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_pkey PRIMARY KEY (id);

CREATE INDEX access_review_tenant_completed_idx ON platform.access_review USING btree (tenant_id, completed_at DESC);

CREATE UNIQUE INDEX change_request_one_pending_idx ON platform.change_request USING btree (action, target_id) WHERE ((status)::text = 'PENDING'::text);

CREATE INDEX change_request_tenant_status_idx ON platform.change_request USING btree (tenant_id, status, requested_at DESC);

CREATE INDEX idx_access_request_application ON platform.access_request USING btree (application_id) WHERE (application_id IS NOT NULL);

CREATE INDEX idx_access_request_status ON platform.access_request USING btree (tenant_id, status);

CREATE INDEX idx_access_request_tenant ON platform.access_request USING btree (tenant_id);

CREATE INDEX idx_account_deletion_request_tenant ON platform.account_deletion_request USING btree (tenant_id, requested_at);

CREATE INDEX idx_admin_console_access_app ON platform.admin_console_access USING btree (application_id);

CREATE INDEX idx_admin_console_access_tenant ON platform.admin_console_access USING btree (tenant_id);

CREATE INDEX idx_admin_console_access_user ON platform.admin_console_access USING btree (user_id);

CREATE INDEX idx_admin_console_feature_override_tenant ON platform.admin_console_feature_override USING btree (tenant_id);

CREATE INDEX idx_admin_console_feature_override_user ON platform.admin_console_feature_override USING btree (user_id);

CREATE INDEX idx_api_key_service_account ON platform.api_key USING btree (service_account_id);

CREATE INDEX idx_application_log_app_created ON platform.application_log USING btree (application_id, created_at DESC) WHERE (application_id IS NOT NULL);

CREATE INDEX idx_application_log_created_at ON platform.application_log USING btree (created_at DESC);

CREATE INDEX idx_application_log_level ON platform.application_log USING btree (level);

CREATE INDEX idx_application_log_request_id ON platform.application_log USING btree (request_id) WHERE (request_id IS NOT NULL);

CREATE INDEX idx_application_log_session_id ON platform.application_log USING btree (session_id) WHERE (session_id IS NOT NULL);

CREATE INDEX idx_application_tenant ON platform.application USING btree (tenant_id);

CREATE INDEX idx_audit_application_created ON platform.audit_log USING btree (application_id, created_at DESC);

CREATE INDEX idx_audit_log_created ON platform.audit_log USING btree (created_at DESC);

CREATE INDEX idx_audit_tenant_created ON platform.audit_log USING btree (tenant_id, created_at);

CREATE INDEX idx_email_token_hash ON platform.email_token USING btree (token_hash);

CREATE INDEX idx_email_token_user_type ON platform.email_token USING btree (user_id, type);

CREATE INDEX idx_identity_provider_application ON platform.identity_provider USING btree (application_id);

CREATE INDEX idx_identity_provider_tenant ON platform.identity_provider USING btree (tenant_id);

CREATE INDEX idx_login_history_application_created ON platform.login_history USING btree (application_id, created_at DESC);

CREATE INDEX idx_login_history_created ON platform.login_history USING btree (created_at);

CREATE INDEX idx_login_history_session_id ON platform.login_history USING btree (session_id) WHERE (session_id IS NOT NULL);

CREATE INDEX idx_login_history_tenant_created ON platform.login_history USING btree (tenant_id, created_at);

CREATE INDEX idx_mfa_factor_user ON platform.mfa_factor USING btree (user_id);

CREATE INDEX idx_oauth_client_application ON platform.oauth_client USING btree (application_id);

CREATE INDEX idx_otp_challenge_active ON platform.otp_challenge USING btree (user_id, purpose) WHERE (consumed_at IS NULL);

CREATE INDEX idx_otp_challenge_expires ON platform.otp_challenge USING btree (expires_at) WHERE (consumed_at IS NULL);

CREATE INDEX idx_rbac_group_application ON platform.rbac_group USING btree (application_id);

CREATE INDEX idx_rbac_group_member_user ON platform.rbac_group_member USING btree (user_id);

CREATE INDEX idx_rbac_group_role_role ON platform.rbac_group_role USING btree (role_id);

CREATE INDEX idx_role_composite_child ON platform.role_composite USING btree (child_role_id);

CREATE INDEX idx_role_tenant ON platform.role USING btree (tenant_id);

CREATE INDEX idx_scim_group_member_user ON platform.scim_group_member USING btree (user_id);

CREATE INDEX idx_scim_group_tenant ON platform.scim_group USING btree (tenant_id);

CREATE INDEX idx_service_account_tenant ON platform.service_account USING btree (tenant_id);

CREATE INDEX idx_tenant_console_role_feature_tenant ON platform.tenant_console_role_feature USING btree (tenant_id);

CREATE INDEX idx_tenant_permission_tenant ON platform.tenant_permission USING btree (tenant_id);

CREATE INDEX idx_tenant_role_tenant ON platform.tenant_role USING btree (tenant_id);

CREATE INDEX idx_tenant_user_roster_source ON platform.tenant_user_roster USING btree (tenant_id, source);

CREATE INDEX idx_tenant_user_roster_user ON platform.tenant_user_roster USING btree (user_id);

CREATE INDEX idx_trusted_device_user ON platform.trusted_device USING btree (user_id);

CREATE INDEX idx_user_application_app ON platform.user_application USING btree (application_id);

CREATE INDEX idx_user_credential_expires_at ON platform.user_credential USING btree (expires_at) WHERE ((is_current = true) AND (expires_at IS NOT NULL));

CREATE INDEX idx_user_credential_user ON platform.user_credential USING btree (user_id);

CREATE INDEX idx_user_identity_provider ON platform.user_identity USING btree (provider_id);

CREATE INDEX idx_user_identity_user ON platform.user_identity USING btree (user_id);

CREATE INDEX idx_user_role_role ON platform.user_role USING btree (role_id);

CREATE INDEX idx_user_role_user ON platform.user_role USING btree (user_id);

CREATE INDEX idx_user_tenant_role_role ON platform.user_tenant_role USING btree (role_id);

CREATE INDEX idx_user_tenant_role_user ON platform.user_tenant_role USING btree (user_id);

CREATE INDEX idx_user_tenant_status ON platform.user_account USING btree (tenant_id, status);

CREATE INDEX idx_webhook_delivery_created ON platform.webhook_delivery USING btree (created_at DESC);

CREATE INDEX idx_webhook_delivery_due ON platform.webhook_delivery USING btree (next_attempt_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRYING'::character varying])::text[]));

CREATE INDEX idx_webhook_delivery_endpoint ON platform.webhook_delivery USING btree (endpoint_id);

CREATE INDEX idx_webhook_endpoint_application ON platform.webhook_endpoint USING btree (application_id) WHERE (application_id IS NOT NULL);

CREATE INDEX idx_webhook_endpoint_tenant ON platform.webhook_endpoint USING btree (tenant_id);

CREATE INDEX ix_application_api_application ON platform.application_api USING btree (application_id);

CREATE INDEX ix_client_registration_token_application ON platform.client_registration_token USING btree (application_id);

CREATE INDEX ix_saml_sp_application ON platform.saml_service_provider USING btree (application_id);

CREATE INDEX ix_tenant_domain_tenant ON platform.tenant_domain USING btree (tenant_id);

CREATE INDEX oauth2_authorization_access_idx ON platform.oauth2_authorization USING hash (access_token_value);

CREATE INDEX oauth2_authorization_client_idx ON platform.oauth2_authorization USING btree (registered_client_id);

CREATE INDEX oauth2_authorization_code_idx ON platform.oauth2_authorization USING hash (authorization_code_value);

CREATE INDEX oauth2_authorization_device_code_idx ON platform.oauth2_authorization USING hash (device_code_value);

CREATE INDEX oauth2_authorization_id_token_idx ON platform.oauth2_authorization USING hash (oidc_id_token_value);

CREATE INDEX oauth2_authorization_last_expiry_idx ON platform.oauth2_authorization USING btree (GREATEST(authorization_code_expires_at, access_token_expires_at, oidc_id_token_expires_at, refresh_token_expires_at, user_code_expires_at, device_code_expires_at));

CREATE INDEX oauth2_authorization_principal_idx ON platform.oauth2_authorization USING btree (principal_name);

CREATE INDEX oauth2_authorization_refresh_idx ON platform.oauth2_authorization USING hash (refresh_token_value);

CREATE INDEX oauth2_authorization_state_idx ON platform.oauth2_authorization USING hash (state);

CREATE INDEX oauth2_authorization_user_code_idx ON platform.oauth2_authorization USING hash (user_code_value);

CREATE UNIQUE INDEX uq_admin_console_access_app_scope ON platform.admin_console_access USING btree (user_id, tenant_id, application_id, role_type) WHERE (application_id IS NOT NULL);

CREATE UNIQUE INDEX uq_admin_console_access_tenant_super ON platform.admin_console_access USING btree (user_id, tenant_id) WHERE ((role_type)::text = 'TENANT_SUPER_ADMIN'::text);

CREATE UNIQUE INDEX uq_api_key_hash ON platform.api_key USING btree (key_hash);

CREATE UNIQUE INDEX uq_application_schema_name ON platform.application USING btree (schema_name) WHERE (schema_name IS NOT NULL);

CREATE UNIQUE INDEX uq_identity_provider_tenant_alias_platform ON platform.identity_provider USING btree (tenant_id, alias) WHERE (application_id IS NULL);

CREATE UNIQUE INDEX uq_identity_provider_tenant_app_alias ON platform.identity_provider USING btree (tenant_id, application_id, alias) WHERE (application_id IS NOT NULL);

CREATE UNIQUE INDEX uq_scim_group_tenant_external ON platform.scim_group USING btree (tenant_id, external_id) WHERE (external_id IS NOT NULL);

CREATE UNIQUE INDEX uq_service_account_tenant_name ON platform.service_account USING btree (tenant_id, name);

CREATE UNIQUE INDEX uq_trusted_device_user_token ON platform.trusted_device USING btree (user_id, device_token_hash);

CREATE UNIQUE INDEX uq_user_account_tenant_phone ON platform.user_account USING btree (tenant_id, phone) WHERE ((phone IS NOT NULL) AND ((phone)::text <> ''::text));

CREATE INDEX user_two_step_state_deadline_idx ON platform.user_two_step_state USING btree (deadline) WHERE (deadline IS NOT NULL);

CREATE UNIQUE INDEX ux_application_api_identifier ON platform.application_api USING btree (lower((identifier)::text));

CREATE UNIQUE INDEX ux_client_registration_open_per_tenant ON platform.application_client_registration USING btree (tenant_id) WHERE ((mode)::text = 'open'::text);

CREATE UNIQUE INDEX ux_saml_sp_tenant_entity ON platform.saml_service_provider USING btree (tenant_id, lower((entity_id)::text));

CREATE UNIQUE INDEX ux_tenant_domain_domain ON platform.tenant_domain USING btree (lower((domain)::text));

CREATE UNIQUE INDEX ux_user_credential_one_current ON platform.user_credential USING btree (user_id) WHERE (is_current = true);

CREATE TRIGGER audit_log_append_only BEFORE DELETE OR UPDATE ON platform.audit_log FOR EACH ROW EXECUTE FUNCTION platform.audit_log_append_only();

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_granted_user_id_fkey FOREIGN KEY (granted_user_id) REFERENCES platform.user_account(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_requester_user_id_fkey FOREIGN KEY (requester_user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES platform.user_account(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES platform.user_account(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.access_request
    ADD CONSTRAINT access_request_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.access_review
    ADD CONSTRAINT access_review_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.account_deletion_request
    ADD CONSTRAINT account_deletion_request_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.account_deletion_request
    ADD CONSTRAINT account_deletion_request_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.admin_console_access
    ADD CONSTRAINT admin_console_access_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.admin_console_access
    ADD CONSTRAINT admin_console_access_granted_by_fkey FOREIGN KEY (granted_by) REFERENCES platform.user_account(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.admin_console_access
    ADD CONSTRAINT admin_console_access_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.admin_console_access
    ADD CONSTRAINT admin_console_access_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.admin_console_feature_override
    ADD CONSTRAINT admin_console_feature_override_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.admin_console_feature_override
    ADD CONSTRAINT admin_console_feature_override_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.api_key
    ADD CONSTRAINT api_key_service_account_id_fkey FOREIGN KEY (service_account_id) REFERENCES platform.service_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_access_removal
    ADD CONSTRAINT application_access_removal_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_access_removal
    ADD CONSTRAINT application_access_removal_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_access_removal
    ADD CONSTRAINT application_access_removal_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_api
    ADD CONSTRAINT application_api_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_api
    ADD CONSTRAINT application_api_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_client_registration
    ADD CONSTRAINT application_client_registration_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_client_registration
    ADD CONSTRAINT application_client_registration_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_schema
    ADD CONSTRAINT application_schema_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application_setting
    ADD CONSTRAINT application_setting_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.application
    ADD CONSTRAINT application_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.change_request
    ADD CONSTRAINT change_request_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.client_registration_token
    ADD CONSTRAINT client_registration_token_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.email_token
    ADD CONSTRAINT email_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.identity_provider
    ADD CONSTRAINT identity_provider_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.identity_provider
    ADD CONSTRAINT identity_provider_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.login_history
    ADD CONSTRAINT login_history_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.mfa_factor
    ADD CONSTRAINT mfa_factor_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.oauth_client
    ADD CONSTRAINT oauth_client_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.otp_challenge
    ADD CONSTRAINT otp_challenge_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.permission
    ADD CONSTRAINT permission_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.platform_operator
    ADD CONSTRAINT platform_operator_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group
    ADD CONSTRAINT rbac_group_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group_member
    ADD CONSTRAINT rbac_group_member_group_id_fkey FOREIGN KEY (group_id) REFERENCES platform.rbac_group(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group_member
    ADD CONSTRAINT rbac_group_member_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group_role
    ADD CONSTRAINT rbac_group_role_group_id_fkey FOREIGN KEY (group_id) REFERENCES platform.rbac_group(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group_role
    ADD CONSTRAINT rbac_group_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.rbac_group
    ADD CONSTRAINT rbac_group_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role
    ADD CONSTRAINT role_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role_composite
    ADD CONSTRAINT role_composite_child_role_id_fkey FOREIGN KEY (child_role_id) REFERENCES platform.role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role_composite
    ADD CONSTRAINT role_composite_parent_role_id_fkey FOREIGN KEY (parent_role_id) REFERENCES platform.role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role_permission
    ADD CONSTRAINT role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES platform.permission(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role_permission
    ADD CONSTRAINT role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.role
    ADD CONSTRAINT role_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.saml_idp_credential
    ADD CONSTRAINT saml_idp_credential_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.saml_service_provider
    ADD CONSTRAINT saml_service_provider_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.saml_service_provider
    ADD CONSTRAINT saml_service_provider_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.scim_group_member
    ADD CONSTRAINT scim_group_member_group_id_fkey FOREIGN KEY (group_id) REFERENCES platform.scim_group(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.scim_group_member
    ADD CONSTRAINT scim_group_member_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.scim_group
    ADD CONSTRAINT scim_group_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.service_account
    ADD CONSTRAINT service_account_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_console_role_feature
    ADD CONSTRAINT tenant_console_role_feature_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_domain
    ADD CONSTRAINT tenant_domain_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_permission
    ADD CONSTRAINT tenant_permission_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_quota
    ADD CONSTRAINT tenant_quota_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_role_application
    ADD CONSTRAINT tenant_role_application_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_role_application
    ADD CONSTRAINT tenant_role_application_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.tenant_role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_role_permission
    ADD CONSTRAINT tenant_role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES platform.tenant_permission(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_role_permission
    ADD CONSTRAINT tenant_role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.tenant_role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_role
    ADD CONSTRAINT tenant_role_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_setting
    ADD CONSTRAINT tenant_setting_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_user_roster
    ADD CONSTRAINT tenant_user_roster_added_by_fkey FOREIGN KEY (added_by) REFERENCES platform.user_account(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.tenant_user_roster
    ADD CONSTRAINT tenant_user_roster_source_application_id_fkey FOREIGN KEY (source_application_id) REFERENCES platform.application(id) ON DELETE SET NULL;

ALTER TABLE ONLY platform.tenant_user_roster
    ADD CONSTRAINT tenant_user_roster_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.tenant_user_roster
    ADD CONSTRAINT tenant_user_roster_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.trusted_device
    ADD CONSTRAINT trusted_device_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.trusted_device
    ADD CONSTRAINT trusted_device_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_account
    ADD CONSTRAINT user_account_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_application
    ADD CONSTRAINT user_application_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_application
    ADD CONSTRAINT user_application_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_credential
    ADD CONSTRAINT user_credential_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_identity
    ADD CONSTRAINT user_identity_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES platform.identity_provider(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_identity
    ADD CONSTRAINT user_identity_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_role
    ADD CONSTRAINT user_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_role
    ADD CONSTRAINT user_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_tenant_role
    ADD CONSTRAINT user_tenant_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES platform.tenant_role(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_tenant_role
    ADD CONSTRAINT user_tenant_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.user_two_step_state
    ADD CONSTRAINT user_two_step_state_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform.user_account(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.webhook_delivery
    ADD CONSTRAINT webhook_delivery_endpoint_id_fkey FOREIGN KEY (endpoint_id) REFERENCES platform.webhook_endpoint(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_application_id_fkey FOREIGN KEY (application_id) REFERENCES platform.application(id) ON DELETE CASCADE;

ALTER TABLE ONLY platform.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id) ON DELETE CASCADE;

ALTER TABLE platform.access_request ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.admin_console_access ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.admin_console_feature_override ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.application ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.audit_log ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.identity_provider ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.login_history ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.mfa_factor ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.otp_challenge ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.rbac_group ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.role ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.scim_group ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.service_account ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.tenant_console_role_feature ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON platform.access_request USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.admin_console_access USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.admin_console_feature_override USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.application USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.audit_log USING ((platform.secureone_rls_bypass() OR ((tenant_id IS NOT NULL) AND platform.secureone_tenant_matches(tenant_id))));

CREATE POLICY tenant_isolation ON platform.identity_provider USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.login_history USING ((platform.secureone_rls_bypass() OR ((tenant_id IS NOT NULL) AND platform.secureone_tenant_matches(tenant_id))));

CREATE POLICY tenant_isolation ON platform.mfa_factor USING ((platform.secureone_rls_bypass() OR (EXISTS ( SELECT 1
   FROM platform.user_account u
  WHERE ((u.id = mfa_factor.user_id) AND ((u.tenant_id)::text = current_setting('app.tenant_id'::text, true)))))));

CREATE POLICY tenant_isolation ON platform.otp_challenge USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.rbac_group USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.role USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.scim_group USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.service_account USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.tenant_console_role_feature USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.tenant_permission USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.tenant_quota USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.tenant_role USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.tenant_user_roster USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.user_account USING (platform.secureone_tenant_matches(tenant_id));

CREATE POLICY tenant_isolation ON platform.user_identity USING ((platform.secureone_rls_bypass() OR (EXISTS ( SELECT 1
   FROM platform.user_account u
  WHERE ((u.id = user_identity.user_id) AND ((u.tenant_id)::text = current_setting('app.tenant_id'::text, true)))))));

CREATE POLICY tenant_isolation ON platform.webhook_endpoint USING (platform.secureone_tenant_matches(tenant_id));

ALTER TABLE platform.tenant_permission ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.tenant_quota ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.tenant_role ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.tenant_user_roster ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.user_account ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.user_identity ENABLE ROW LEVEL SECURITY;

ALTER TABLE platform.webhook_endpoint ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- Reference data
-- ----------------------------------------------------------------------------

INSERT INTO platform.tenant (id, slug, name, status, settings, created_at, updated_at, issuer_mode) VALUES ('7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'platform', 'Platform operators', 'ACTIVE', '{"plan": "enterprise", "system": true}', '2026-10-28 21:00:24.245634+00', '2026-10-28 21:00:24.245634+00', 'shared');

INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('email', '{"replyTo": "support@secureone.local", "fromName": "SecureOne", "fromAddress": "noreply@secureone.local"}', '2026-10-28 21:00:17.325803+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('appearance', '{}', '2026-10-28 21:00:21.656837+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('password_policy', '{"minLength": 12, "expiryDays": 0, "historyCount": 5, "hashAlgorithm": "bcrypt", "requireNumber": true, "requireSymbol": true, "requireUppercase": true}', '2026-10-28 21:00:17.588893+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('user_directory', '{"ldap": {"host": "", "port": 389, "baseDn": "", "bindDn": "", "useTls": true, "userFilter": "(mail={0})", "bindPassword": ""}, "sources": {"csv": {"enabled": true}, "ldap": {"enabled": false}, "excel": {"enabled": true}}, "exportEnabled": false, "importEnabled": false}', '2026-10-28 21:00:18.207881+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('public_manifest_defaults', '{"enabled": false, "sections": {"appearance": false, "application": true, "authMethods": true, "featureFlags": true, "passwordPolicy": true}, "authMethodsOnlyEnabled": true}', '2026-10-28 21:00:18.229526+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('sms', '{"enabled": false, "provider": "log", "twilioAuthToken": "", "twilioAccountSid": "", "twilioFromNumber": ""}', '2026-10-28 21:00:21.962368+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('token_policy', '{"idTokenTtlSeconds": 3600, "reuseRefreshTokens": false, "rotateRefreshTokens": true, "deviceCodeTtlSeconds": 600, "refreshTokensEnabled": true, "accessTokenTtlSeconds": 3600, "refreshTokenTtlSeconds": 604800, "refreshTokenReuseDetection": true, "authorizationCodeTtlSeconds": 300, "clientCredentialsTtlSeconds": 3600}', '2026-10-28 21:00:18.24518+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('smtp', '{"host": "", "port": 465, "security": "ssl", "username": "", "authEnabled": true}', '2026-10-28 21:00:19.080228+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('email_templates', '{}', '2026-10-28 21:00:19.080228+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('feature_flags', '[{"key": "self_registration", "name": "Self Registration", "enabled": false, "description": "Public signup"}, {"key": "self_service_recovery", "name": "Self-service Recovery", "enabled": true, "description": "Password reset & magic link"}]', '2026-10-28 21:00:17.588893+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('notifications', '{"emailEnabled": true, "adminRecipients": [], "auditAlertsEnabled": true, "securityAlertsEnabled": true}', '2026-10-28 21:00:19.529752+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('app_settings_exposure', '{"sms": true, "email": true, "appearance": false, "two-factor": true, "auth-methods": true, "token-policy": true, "feature-flags": true, "notifications": true, "user-directory": true, "password-policy": true, "public-manifest": true, "verification-policy": true}', '2026-10-28 21:00:18.111359+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('scim', '{"enabled": false, "userNameDomain": "secureone.local", "bearerTokenEncrypted": ""}', '2026-10-28 21:00:22.803081+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('auth_methods', '[{"id": "m_password", "name": "Password", "enabled": true, "category": "primary", "description": "Username + password", "implemented": true}, {"id": "m_passkey", "name": "Passkeys (WebAuthn)", "enabled": true, "category": "primary", "description": "Phishing-resistant sign-in", "implemented": false}, {"id": "m_magic", "name": "Magic Link", "enabled": true, "category": "primary", "description": "Email passwordless login", "implemented": true}, {"id": "m_sms", "name": "SMS OTP", "enabled": false, "category": "mfa", "description": "SMS one-time codes", "implemented": false}, {"id": "m_email_otp", "name": "Email OTP", "enabled": true, "category": "mfa", "description": "Email one-time codes at login", "implemented": false}, {"id": "m_google", "name": "Google", "enabled": true, "category": "federation", "description": "Google OIDC", "implemented": false}, {"id": "m_github", "name": "GitHub", "enabled": true, "category": "federation", "description": "GitHub OAuth", "implemented": false}, {"id": "m_saml", "name": "SAML 2.0", "enabled": false, "category": "federation", "description": "Enterprise SSO", "implemented": false}, {"id": "m_oidc", "name": "External OIDC", "enabled": false, "category": "federation", "description": "OIDC federation", "implemented": false}]', '2026-10-28 21:00:17.588893+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('mfa_policy', '{"mode": "optional", "challengeTtlSeconds": 300, "stepUpAfterPassword": true, "maxChallengeAttempts": 5, "recoveryCodesPerEnrollment": 10}', '2026-10-28 21:00:22.645133+00');
INSERT INTO platform.platform_setting (key, value, updated_at) VALUES ('verification_policy', '{"login": {"otpLength": 6, "maxAttempts": 5, "otpTtlSeconds": 600, "identifierTypes": ["email", "phone"], "passwordlessModes": ["email_otp", "phone_otp", "magic_link"], "resendCooldownSeconds": 60}, "signup": {"otpLength": 6, "defaultMode": "link", "maxAttempts": 5, "allowedModes": ["link", "otp"], "otpTtlSeconds": 600, "optionalChannels": ["phone"], "requiredChannels": ["email"], "resendCooldownSeconds": 60, "allowUserChannelChoice": false}, "recovery": {"otpLength": 6, "maxAttempts": 5, "allowedModes": ["link", "otp"], "otpTtlSeconds": 600, "fallbackToLink": true, "allowedChannels": ["email", "phone"], "passwordResetMode": "link", "resendCooldownSeconds": 60, "allowUserChannelChoice": true, "requireVerifiedChannel": true}}', '2026-10-28 21:00:21.962368+00');

INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('9195a1f0-e6f5-4be8-8857-2fcdc5d218a1', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'tenant:read', 'View tenant metadata and roster');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('d09437cf-7bd4-4316-82a8-f5a54308bacc', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'tenant:manage', 'Edit tenant settings and roster');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('4d67fd60-0c12-4fe7-8a65-412516b6dbef', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'operator:read', 'View tenant operators and console access');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('bfc5e78f-4396-497d-93d8-5c23d6a9ef4b', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'operator:manage', 'Grant or revoke operator console access');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('8bc046da-b214-4ae8-8267-18a8fb1a7f23', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'application:access', 'Assign users to applications');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('e3da17e8-7589-4136-a965-bf56bec28be7', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'application:manage', 'Create and configure applications');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('d6df4bf5-2ad1-489b-8081-f310caf24571', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:users', 'Admin console — Users section');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('8d925a9e-275b-4c3a-b5fe-4dad3c9c2473', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:roles', 'Admin console — Roles section');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('1bfa03c8-2b31-47c6-847a-9c724eaad5e4', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:groups', 'Admin console — Groups section');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('7465ff09-6b50-4533-91bc-c04925a9a760', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:permissions', 'Admin console — Permissions section');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('7e4a764f-60d8-400d-812a-d1e77cce97bf', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:settings', 'Admin console — Settings section');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('0fd0ce65-1f84-4b2f-a065-ad43df572d4f', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:audit', 'Admin console — Audit log');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('7cc05ab4-8396-4547-b697-766ee8f0dc57', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:logs', 'Admin console — Application logs');
INSERT INTO platform.tenant_permission (id, tenant_id, key, description) VALUES ('71ff2298-d2a4-4e82-b42c-797bf952221f', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'console:sessions', 'Admin console — Sessions');

INSERT INTO platform.tenant_role (id, tenant_id, name, description, is_system) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'Tenant Administrator', 'Full tenant governance — operators, applications, and console features', true);
INSERT INTO platform.tenant_role (id, tenant_id, name, description, is_system) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'Application Operator', 'Manage users, groups, and settings for assigned applications', true);
INSERT INTO platform.tenant_role (id, tenant_id, name, description, is_system) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '7c27aaa5-97f3-468e-b48b-bcd87d685e62', 'Tenant Auditor', 'Read-only access to tenant roster, audit, and logs', true);

INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '9195a1f0-e6f5-4be8-8857-2fcdc5d218a1');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '9195a1f0-e6f5-4be8-8857-2fcdc5d218a1');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '9195a1f0-e6f5-4be8-8857-2fcdc5d218a1');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', 'd09437cf-7bd4-4316-82a8-f5a54308bacc');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '4d67fd60-0c12-4fe7-8a65-412516b6dbef');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '4d67fd60-0c12-4fe7-8a65-412516b6dbef');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '4d67fd60-0c12-4fe7-8a65-412516b6dbef');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', 'bfc5e78f-4396-497d-93d8-5c23d6a9ef4b');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '8bc046da-b214-4ae8-8267-18a8fb1a7f23');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '8bc046da-b214-4ae8-8267-18a8fb1a7f23');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', 'e3da17e8-7589-4136-a965-bf56bec28be7');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', 'd6df4bf5-2ad1-489b-8081-f310caf24571');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', 'd6df4bf5-2ad1-489b-8081-f310caf24571');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '8d925a9e-275b-4c3a-b5fe-4dad3c9c2473');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '8d925a9e-275b-4c3a-b5fe-4dad3c9c2473');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '1bfa03c8-2b31-47c6-847a-9c724eaad5e4');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '1bfa03c8-2b31-47c6-847a-9c724eaad5e4');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '7465ff09-6b50-4533-91bc-c04925a9a760');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '7e4a764f-60d8-400d-812a-d1e77cce97bf');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '7e4a764f-60d8-400d-812a-d1e77cce97bf');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '0fd0ce65-1f84-4b2f-a065-ad43df572d4f');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '0fd0ce65-1f84-4b2f-a065-ad43df572d4f');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '0fd0ce65-1f84-4b2f-a065-ad43df572d4f');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '7cc05ab4-8396-4547-b697-766ee8f0dc57');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '7cc05ab4-8396-4547-b697-766ee8f0dc57');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('78ab5603-626f-4cf0-b3dd-808d42894835', '71ff2298-d2a4-4e82-b42c-797bf952221f');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('3fb82b09-266f-429f-8c59-7307f8500206', '71ff2298-d2a4-4e82-b42c-797bf952221f');
INSERT INTO platform.tenant_role_permission (role_id, permission_id) VALUES ('962aa498-7e00-40af-b32c-425bdd69695f', '71ff2298-d2a4-4e82-b42c-797bf952221f');

-- ----------------------------------------------------------------------------
-- Application role: request-time queries SET ROLE to it so row-level security always applies.
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'secureone_app') THEN
        CREATE ROLE secureone_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

-- Lets a non-superuser schema owner SET ROLE to it (superusers can anyway).
GRANT secureone_app TO CURRENT_USER;

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

-- The audit log is append-only for the application.
REVOKE UPDATE, TRUNCATE ON platform.audit_log FROM secureone_app;
