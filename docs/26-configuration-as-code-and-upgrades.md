# 26 — Configuration as code and upgrades

Two things that make SecureOne safe to run for years: keeping each tenant's setup in a file you can review and replay, and upgrading by swapping the image.

---

## Part 1 — Configuration as code

> **Off by default in the console and the API.** A platform operator turns it on in **Platform settings › Feature flags › Platform features** (setting `platform_features.configAsCode`, API `PUT /api/admin/v1/settings/platform-features` with `{"configAsCode": true}`). While it is off, `/admin/config` is not found and `/api/admin/v1/config/…` answers 404. Start-up import with `SECUREONE_CONFIG_IMPORT` is not affected.

### What a configuration file holds

Two kinds of file (YAML or JSON): one **per tenant** (`kind: TenantConfiguration`) and one for the **platform** (`kind: PlatformConfiguration`).

| Tenant file | Platform file | Never in a file |
|-------------|---------------|-----------------|
| Tenant: name, plan, status | Default SMTP server for all applications | Users, group members, role assignments |
| Applications: name, description, status | Default SMS provider (Twilio, AWS SNS, MSG91, log) | **Credential values** (see below) |
| OAuth clients: type, grant types, scopes, redirect URIs, PKCE, auth method, subject type | SCIM: on/off, username domain, platform bearer token | Console operator roles (Tenant Admin, Application Admin, Super Admin): managed under console access |
| Permissions, roles (with permissions and included roles), groups (with roles) | | Settings inherited from the platform (they stay inherited) |
| Settings the application **overrides** (auth methods, password / MFA / verification / token policy, feature flags, appearance, user directory, notifications, email, client integration) and its own **SMTP server** and **SMS provider** (`settings.smtp`, `settings.sms`) | | |
| **Sign-in providers** (Google, GitHub, OIDC, SAML): for the whole tenant, or under one application | | |
| **Webhooks** (URL, events, optional application) | | |
| **Service accounts** and their **API keys** (by label, with expiry) | | |
| The tenant's **SCIM token** | | |

### Credentials: environment variables, never the file

A file never contains a password, token or key. It names an **environment variable on the server** that holds it; changing the variable's value and applying again rotates the credential. A literal value in the file is refused with the field to use instead.

| Credential | Field in the file | Value rules |
|------------|-------------------|-------------|
| OAuth client secret | `clientSecretEnv` | at least 32 characters |
| Sign-in provider client secret | `identityProviders[].clientSecretEnv` | |
| Webhook signing secret | `webhooks[].secretEnv` | at least 32 characters |
| Service account API key | `serviceAccounts[].apiKeys[].keyEnv` | `sk_live_…`, at least 40 characters; a new value revokes the key with that label |
| Tenant SCIM token | `scim.tokenEnv` | `scim_…`, at least 40 characters |
| SMTP password | `smtp.passwordEnv` | |
| Twilio auth token / AWS secret key / MSG91 API key | `sms.twilio.authTokenEnv` / `sms.aws_sns.secretAccessKeyEnv` / `sms.msg91.apiKeyEnv` | |
| Platform SCIM bearer token | `scim.bearerTokenEnv` (platform file) | at least 32 characters |

Without a variable: an existing credential is left as it is; a new client, webhook, API key or SCIM token gets a generated value, shown **once** in the result (never logged; with start-up import, the log only says it was created). Exports never contain credentials, and credential fields inside settings (for example the LDAP bind password) are exported blank; importing a blank keeps the stored value.

```yaml
apiVersion: secureone/v1
kind: TenantConfiguration
tenant:
  slug: tenant-a
  name: Tenant A
  plan: team
applications:
  - slug: shop
    name: Shop
    clients:
      - clientId: shop-web
        type: spa
        redirectUris: [https://shop.example.com/callback]
        postLogoutRedirectUris: [https://shop.example.com/]
      - clientId: shop-api
        type: m2m
        scopes: [orders.sync]
        clientSecretEnv: SHOP_API_SECRET   # the server reads the secret from this variable
    permissions:
      - key: orders:read
        description: See orders
      - key: orders:write
    roles:
      - name: viewer
        permissions: [orders:read]
      - name: editor
        permissions: [orders:write]
        includes: [viewer]                 # makes the role composite
    groups:
      - name: staff
        roles: [editor]
    identityProviders:                     # sign-in providers for this application only
      - alias: corp-oidc
        type: OIDC
        displayName: Corporate login
        enabled: true
        config: {clientId: shop, issuer: https://login.example.com}
        clientSecretEnv: CORP_OIDC_SECRET
    settings:
      password_policy:
        minLength: 14
      smtp:                                # this application's own mail server
        host: smtp.shop.example.com
        port: 587
        security: starttls
        username: mailer
        passwordEnv: SHOP_SMTP_PASSWORD
      sms:
        enabled: true
        provider: twilio
        twilio: {accountSid: AC123, fromNumber: "+15550001111", authTokenEnv: SHOP_TWILIO_TOKEN}
scim:
  enabled: true                            # false revokes the token
  tokenEnv: TENANT_A_SCIM_TOKEN
identityProviders:                         # for every application of the tenant
  - alias: google
    type: GOOGLE
    enabled: true
    config: {clientId: 1234.apps.googleusercontent.com}
    clientSecretEnv: GOOGLE_CLIENT_SECRET
webhooks:
  - url: https://hooks.example.com/secureone
    application: shop                      # optional: only this application's events
    events: [user.created, access_request.created]
    secretEnv: TENANT_A_WEBHOOK_SECRET
serviceAccounts:
  - name: billing-sync
    description: Nightly billing job
    apiKeys:
      - label: prod
        expiresAt: 2027-06-30T00:00:00Z
        keyEnv: BILLING_SYNC_API_KEY
```

The platform file:

```yaml
apiVersion: secureone/v1
kind: PlatformConfiguration
smtp: {host: smtp.example.com, port: 587, security: starttls, username: secureone, authEnabled: true, passwordEnv: SMTP_PASSWORD}
sms:
  enabled: true
  provider: aws_sns
  aws_sns: {region: eu-west-1, accessKeyId: AKIA..., senderId: SecureOne, secretAccessKeyEnv: AWS_SNS_SECRET}
scim: {enabled: true, userNameDomain: example.com, bearerTokenEnv: SCIM_PLATFORM_TOKEN}
```

### Rules

- **Left out means "leave as it is".** A field or list that is not in the file is not changed. A list that is in the file means "exactly these" (with prune, see below).
- **Matched by name:** tenants and applications by slug, clients by `clientId`, permissions by key, roles and groups by name, sign-in providers by alias, webhooks by URL (and application), service accounts by name, API keys by label. Renaming in the file creates a new one. A sign-in provider's type cannot change.
- **Prune** (`prune: true` in the file, or the console checkbox, or `?prune=true`): also deletes clients, permissions, roles, groups, sign-in providers, webhooks and service accounts that the file's lists leave out, revokes API keys whose label is not listed, and clears settings overrides (including `smtp` / `sms`) the file's `settings` leave out. To remove everything of one kind, give an empty list (`webhooks: []`); a list that is not in the file at all is left alone. Even with prune, applications, users, built-in roles, console operator roles and permissions a remaining role still uses are kept (the preview says so). A role that users hold is never deleted; the preview reports it as a problem.
- **Secrets:** a confidential client created without `clientSecretEnv` gets a generated secret, shown once in the console result (never logged). With `clientSecretEnv`, the secret comes from that environment variable on the server (at least 32 characters); changing the variable's value and applying again rotates it.
- **Idempotent:** applying the same file twice changes nothing the second time. Exporting a tenant and previewing the export shows no changes.

### Using it

**Console:** *Configuration as code* (platform operators): pick a tenant (or *Platform*) → **Download YAML**, or open it in the editor; paste or upload a file → **Preview changes** → **Apply**. Apply is enabled only after a preview of exactly that text shows changes and no problems.

**API** (platform operator session):

```bash
# Export
curl -H "Authorization: SecureOne-Admin-Session $TOKEN" \
  https://id.example.com/api/admin/v1/config/tenants/tenant-a/export > tenant-a.yaml   # ?format=json for JSON
curl -H "Authorization: SecureOne-Admin-Session $TOKEN" \
  https://id.example.com/api/admin/v1/config/platform/export > platform.yaml

# Preview (dry run, the default) and apply
curl -X POST -H "Authorization: SecureOne-Admin-Session $TOKEN" -H "Content-Type: application/yaml" \
  --data-binary @tenant-a.yaml "https://id.example.com/api/admin/v1/config/import?dryRun=true"
curl -X POST ... "https://id.example.com/api/admin/v1/config/import?dryRun=false&prune=false"
```

The response lists `changes` (`create` / `update` / `delete` with field-level details), `errors`, `notes` and created `secrets`. Status **400**: the file is not valid YAML/JSON or has an unknown field (the message names the field, its place and the allowed names). **422**: the file has problems (an unknown permission in a role, a missing secret variable, …) and nothing was changed, or applying stopped part-way.

**At every start-up (GitOps):** set `SECUREONE_CONFIG_IMPORT` to a file or a folder of `*.yaml` / `*.yml` / `*.json` files (tenant and platform files can be mixed). Put the credential variables the files name in the same Kubernetes Secret as the other `SECUREONE_*` secrets. Each start applies them in name order; instances that start together take turns. A file with problems is logged and skipped; the server still starts. With Helm, put the files in a ConfigMap and set `configImport.configMap`.

### If applying stops part-way

Each application has its own database schema, so an import is not one transaction. The whole file is checked first and nothing is changed if it has problems; then the changes run in order (tenant, applications, service accounts, clients, permissions, roles; then role includes, groups, settings, sign-in providers, webhooks, API keys and the SCIM token; then deletions). If one fails (for example the database is briefly unavailable), the result says which and how many were applied. Fix the cause and apply the same file again: what is already in place shows as unchanged.

Every apply is recorded in the audit log as `config.imported`.

---

## Part 2 — Upgrades

> **0.1.0 is a fresh start.** Version numbers restarted at 0.1.0 with a new schema baseline: every earlier migration was squashed into one `V1__baseline.sql`. A database created by an earlier release (0.3.x and before) cannot be upgraded in place: 0.1.0 refuses to start on it. Install 0.1.0 on an empty database and set it up again (export tenants as configuration files first if you want to re-apply them). Upgrades from 0.1.0 onwards work as described below.

### How an upgrade works

Run the new image with the same environment. At start-up, before anything else uses the database, SecureOne:

1. **Refuses a database from a newer release.** If a newer SecureOne already upgraded the database, an older image stops with a message naming the versions. Start the newer release again, or restore the backup taken before the upgrade.
2. **Applies the platform migrations** (Flyway, `db/migration/postgresql`).
3. **Upgrades every application schema** (`db/migration/application-schema`): each schema gets the migrations it misses, in its own transaction, and its version is recorded in `platform.application_schema.flyway_version`.

Instances that start together take turns (Postgres locks), so two replicas never migrate the same thing twice. The log shows the result:

```
Database schema: platform V1, application schemas V3 (10 schema(s)), mode auto
```

The same numbers are on the management port: `GET :9001/actuator/info` → `database`, plus `build.version`. `GET /api/info` shows the release version.

### `SECUREONE_DB_MIGRATE`

| Value | Use |
|-------|-----|
| `auto` (default) | Upgrade at start-up. Fine for one instance or Docker Compose. |
| `only` | Upgrade the database, then exit (code 0), without Redis, the web server or the console. Run it as a job before rolling out a new release. |
| `validate` | Change nothing; refuse to start while an upgrade is missing. Use on the long-running instances when a job does the upgrade. |

The Helm chart does this for you: `migrationJob.enabled: true` (the default) runs a pre-install / pre-upgrade Job with `only` and starts the pods with `validate`. If the job fails, `helm upgrade` stops and the old pods keep running.

```bash
# Docker, by hand
docker run --rm --env-file secureone.env -e SECUREONE_DB_MIGRATE=only secureone/secureone:1.5.0
```

### Upgrade checklist

1. **Back up** the database (and keep `SECUREONE_ENCRYPTION_KEY` with it).
2. Read the release notes.
3. Run the upgrade: Helm `helm upgrade` (the job migrates), or `SECUREONE_DB_MIGRATE=only` then start the new image, or simply start the new image (`auto`).
4. Check `:9001/actuator/info` (new versions), sign in to the console, and run one token request.

### Rolling back

Migrations are forward-only. Before an upgrade, an older image runs fine; after the database was upgraded, the older image refuses to start (step 1 above). To roll back after an upgrade, restore the backup from step 1, then start the older image. See [runbooks/rollback.md](runbooks/rollback.md).

### Rules for writing migrations (contributors)

- Never edit a released migration file; add a new one.
- **Platform schema:** add `V<n>__description.sql` under `db/migration/postgresql`.
- **Application schemas:** add `V<n>__description.sql` under `db/migration/application-schema` **and** list it in `ApplicationSchemaMigrations.ALL`. New applications get every migration when they are created; existing ones get the missing ones at the next start.
- **Old and new pods overlap during a rolling update**, so a release's migrations only add (tables, columns with defaults, indexes). Removing or renaming a column takes two releases: stop using it, then remove it in the next release.
