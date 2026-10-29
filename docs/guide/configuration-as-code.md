---
title: Configuration as code
summary: Export, preview and apply configuration files
order: 9
audience: public
---

# Configuration as code

[User guide](index.md) › Configuration as code

**Where:** sidebar **Configuration as code** (`/admin/config`). **Who:** platform operators.

## Turning it on

Configuration as code is **off by default**. Turn it on in **Platform settings › Feature flags › Platform features › Configuration as code**. While it is off:

- the sidebar has no **Configuration as code** entry;
- `/admin/config` shows "not found", even when typed into the address bar;
- the API (`/api/admin/v1/config/…`) answers 404 *Configuration as code is turned off*.

Files applied at start-up with `SECUREONE_CONFIG_IMPORT` still work: that is set on the server by whoever runs it.

*Keep a tenant's setup in a file: export it, review changes in version control, and apply the same file to staging and production.*

The file format is described in [Configuration as code and upgrades](../install/configuration-and-upgrades.md).

## Export

*A tenant: applications, OAuth clients, permissions, roles, groups, settings, sign-in providers, webhooks, service accounts and SCIM. The platform: default SMTP, SMS and SCIM. Users and credentials are never included; files name environment variables for credentials.*

| Control | What it does |
|---------|--------------|
| **Tenant or platform** | Pick a tenant, or **Platform: SMTP, SMS, SCIM** for the platform defaults. |
| **Download YAML** | Saves `secureone-{tenant}.yaml` (or `secureone-platform.yaml`). |
| **Open in the editor below** | Loads the export into the editor so you can review or change it before applying. |

Credentials are never written to the file. Instead the file names an environment variable, for example `clientSecretEnv: SHOP_CLIENT_SECRET`. The server reads the value from that variable when the file is applied.

## Preview and apply a file

*Paste or upload a configuration file, preview what would change, then apply it. Applying again changes nothing: what is already in place shows as unchanged.*

| Control | What it does |
|---------|--------------|
| **Upload file** | Loads a `.yaml`, `.yml` or `.json` file into the editor. |
| **Configuration file** (editor) | Paste or edit the file here. It starts with `apiVersion: secureone/v1` and `kind: TenantConfiguration` (or `PlatformConfiguration`). |
| **Delete what the file does not list** | *Only inside the lists the file contains (clients, permissions, roles, groups, settings). Applications, users and built-in roles are never deleted.* Leave it off unless the file is your single source of truth. |
| **Preview changes** | A dry run: lists every change (create, update, delete) without changing anything. |
| **Apply N change(s)** | Applies exactly what was previewed. Enabled only after a clean preview with changes; it turns red when the preview includes deletions. |

Result messages:

| Message | Meaning |
|---------|---------|
| *Applying would make N change(s):* | Preview result; read the list before applying. |
| *Already up to date: applying this file would change nothing.* / *Nothing to change.* | The server already matches the file. |
| *The file has problems; nothing was changed.* | Fix the listed errors (unknown field, missing environment variable…) and preview again. |
| *Applying stopped.* | An error occurred while applying; changes made before it stay. Fix and apply again; it continues where it stopped. |

## Applying files automatically

To apply files at every server start, mount them in the container and set `SECUREONE_CONFIG_IMPORT` to their path. See [Configuration as code and upgrades](../install/configuration-and-upgrades.md).

## Typical workflow

1. Set up a tenant by hand on a test server.
2. **Export**, then commit the YAML file to Git.
3. Replace secrets with `…Env` variable names (the export does this for you) and define those variables on each server.
4. On staging and production, **Upload file**, **Preview changes**, check the list, then **Apply**.
5. For later changes, edit the file in Git, review the pull request, and apply again.
