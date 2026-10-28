# SecureOne user guide

This guide explains the SecureOne admin console page by page: what each page is for, what every button does, and what to put in every field. It also covers the pages that end users see (sign-in, sign-up, account security).

The console runs at `/admin` on your SecureOne server, for example `http://localhost:9000/admin`.

## Who's who

| Term | Meaning |
|------|---------|
| **Platform** | Your SecureOne installation. It is run by platform operators. |
| **Platform operator** | A named person with full access to every tenant and application. They sign in on the **Platform operator** tab and must use an authenticator app. |
| **Tenant** | An organization that uses the platform (a customer, a business unit). Each tenant's data is isolated from the others by PostgreSQL row-level security. |
| **Application** | A product of a tenant, for example "Example App". It has its own users, roles, permissions, groups and settings, stored in its own database schema. |
| **OAuth client** | The credentials one piece of software uses to sign users in to an application: a web app, a mobile app or a backend job. One application can have several clients that share the same users and roles. |
| **End user** | A person who signs in to your application. End users never see the admin console. |

## Console roles

| Role | Can do |
|------|--------|
| Platform super admin (platform operator) | Everything: tenants, OAuth clients, platform settings, configuration as code, access review, and every application console. |
| Tenant Super Admin | Every application in their tenant: users, roles, settings, OAuth clients, plus the tenant's roles, roster and access review. |
| Tenant Admin | Only the applications assigned to them: users, roles, settings and OAuth clients of those applications. |
| Application Admin | One application's console (users, roles, settings). |

Section access can be narrowed further per person; see [Tenants](05-tenants.md#tenant-roster).

## Chapters

| # | Chapter | Covers |
|---|---------|--------|
| 1 | [Sign-in, first-run setup and My profile](01-sign-in-and-profile.md) | Setup code, operator account, sign-in tabs, authenticator and recovery codes, emergency access, password and MFA in My profile |
| 2 | [Console layout and navigation](02-console-layout.md) | Top bar, application selector, sidebars, what each role sees, info icons and tooltips |
| 3 | [Connect an app](03-connect-an-app.md) | The guided wizard: app kinds, frameworks, every field, the result page, test sign-in |
| 4 | [OAuth clients](04-oauth-clients.md) | The client registry, **Register application & OAuth client**, **Add OAuth client**, client templates, grant types, the client page, rotating secrets |
| 5 | [Tenants](05-tenants.md) | Tenant list and form, the tenant page (applications, roster, console access, federation, organizations, access requests, service accounts, webhooks), tenant roles and permissions |
| 6 | [Application console](06-application-console.md) | Users, Groups, Roles, Permissions, Audit log, Application logs, Sessions |
| 7 | [Settings](07-settings.md) | Platform settings and application settings, tab by tab, field by field |
| 8 | [Access review and platform operators](08-access-review-and-operators.md) | Approving OAuth client deletes, who holds privileged roles, CSV export, adding platform operators |
| 9 | [Configuration as code](09-configuration-as-code.md) | Export, preview and apply configuration files from the console |
| 10 | [End-user pages](10-end-user-pages.md) | Hosted sign-in, sign-up, password recovery, magic link, device sign-in, account security, privacy |
| 11 | [Common tasks](11-common-tasks.md) | Step-by-step recipes and the "Coming soon" list |
| 12 | [Two-step verification](12-two-step-verification.md) | The options, sign-in and setup for users, platform/tenant/application settings and locks, reset, extend and exempt |

## Conventions in this guide

- **Bold** text is exactly what you see on screen: a button, tab, field label or menu entry.
- A field marked *required* must be filled before the form can be saved.
- An **i** icon next to a field shows extra help when you hover it or focus it with the keyboard. The main buttons on the OAuth clients page show a tooltip on hover.
- Secrets (client secrets, API keys, webhook signing secrets, SCIM tokens, recovery codes) are **shown once**. Copy them when they appear; SecureOne stores only a hash and cannot show them again. If you lose one, rotate or regenerate it.

## Related documentation

- [Operator getting started](../22-operator-getting-started.md): install and first run
- [Deployment: image + env & enterprise](../24-deployment-image-env-and-enterprise.md): container settings
- [Application integration guide](../23-application-integration-guide.md): developer detail for wiring an app
- [Configuration as code and upgrades](../26-configuration-as-code-and-upgrades.md): file format reference
