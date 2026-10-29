---
title: Admin console guide
summary: The console page by page: every button, form and field
order: 4
audience: public
---

# Admin console guide

This guide explains the SecureOne admin console page by page: what each page is for, what every button does, and what to put in every field. It also covers the pages that end users see (sign-in, sign-up, account security).

The console runs at `/admin` on your SecureOne server, for example `/admin`.

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

Section access can be narrowed further per person; see [Tenants](tenants.md#tenant-roster).

## Chapters

| Chapter | Covers |
|---------|--------|
| [Sign-in, first-run setup and My profile](sign-in-and-profile.md) | Setup code, operator account, sign-in tabs, authenticator and recovery codes, emergency access, password and MFA in My profile |
| [Console layout and navigation](console-layout.md) | Top bar, application selector, sidebars, what each role sees, info icons and tooltips |
| [Connect an app](connect-an-app.md) | The guided wizard: app kinds, frameworks, every field, the result page, test sign-in |
| [OAuth clients](oauth-clients.md) | The client registry, **Register application & OAuth client**, **Add OAuth client**, client templates, grant types, the client page, rotating secrets |
| [Tenants](tenants.md) | Tenant list and form, the tenant page (applications, roster, console access, federation, organizations, access requests, service accounts, webhooks), tenant roles and permissions |
| [Application console](application-console.md) | Users, Groups, Roles, Permissions, Audit log, Application logs, Sessions |
| [Settings](settings.md) | Platform settings and application settings, tab by tab, field by field |
| [Access review and platform operators](access-review-and-operators.md) | Approving OAuth client deletes, who holds privileged roles, CSV export, adding platform operators |
| [Configuration as code](configuration-as-code.md) | Export, preview and apply configuration files from the console |
| [End-user pages](end-user-pages.md) | Hosted sign-in, sign-up, password recovery, magic link, device sign-in, account security, privacy |
| [Common tasks](common-tasks.md) | Step-by-step recipes and the "Coming soon" list |
| [Two-step verification](two-step-verification.md) | The options, sign-in and setup for users, platform/tenant/application settings and locks, reset, extend and exempt |

## Conventions in this guide

- **Bold** text is exactly what you see on screen: a button, tab, field label or menu entry.
- A field marked *required* must be filled before the form can be saved.
- An **i** icon next to a field shows extra help when you hover it or focus it with the keyboard. The main buttons on the OAuth clients page show a tooltip on hover.
- Secrets (client secrets, API keys, webhook signing secrets, SCIM tokens, recovery codes) are **shown once**. Copy them when they appear; SecureOne stores only a hash and cannot show them again. If you lose one, rotate or regenerate it.

## Related documentation

- [Operator getting started](../get-started/operator-getting-started.md): install and first run
- [Deployment: image + env & enterprise](../install/deployment.md): container settings
- [Application integration guide](../integrate/integration-guide.md): developer detail for wiring an app
- [Configuration as code and upgrades](../install/configuration-and-upgrades.md): file format reference
