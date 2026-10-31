---
title: Diagram sources
summary: Architecture and sign-in flow diagrams
order: 7
audience: internal
---

# Diagram sources

Every architecture/flow diagram exists in two forms:

- **Mermaid** — embedded inline in the relevant doc; renders automatically on GitHub.
- **`.drawio`** — editable source in this folder; **view inline in SecureOne Documentation** on the parent doc page (e.g. [Architecture](architecture.md) → `/documentation/architecture`), or edit in [draw.io / diagrams.net](https://app.diagrams.net) / VS Code Draw.io extension.

## Viewing in SecureOne Documentation

1. Open **SecureOne Documentation** from the admin sidebar (new tab) → `/admin/documentation`
2. Go to the doc listed in **Used in** below (or open **Diagrams** and click a row — links to that doc).
3. Scroll to the section with **Editable source** — the draw.io viewer is embedded inline below the link.
4. Download the `.drawio` source from the link or `GET /api/documentation/assets/diagrams/{filename}.drawio` (session required).

> Legacy `/documentation/diagram/diagrams/{file}.drawio` URLs redirect to the parent doc page.

## Index

| Diagram | Source | Used in |
|---|---|---|
| Logical architecture | [`logical-architecture.drawio`](../diagrams/logical-architecture.drawio) | [architecture](architecture.md) |
| Auth flow — Authorization Code + PKCE | [`auth-flow-authorization-code-pkce.drawio`](../diagrams/auth-flow-authorization-code-pkce.drawio) | [architecture](architecture.md) |
| Auth flow — Client Credentials (M2M) | [`auth-flow-client-credentials.drawio`](../diagrams/auth-flow-client-credentials.drawio) | [architecture](architecture.md) |
| Auth flow — Refresh token rotation | [`auth-flow-refresh-rotation.drawio`](../diagrams/auth-flow-refresh-rotation.drawio) | [architecture](architecture.md) |
| Auth flow — Hosted vs native sign-in (2 pages) | [`auth-flow-hosted-vs-native.drawio`](../diagrams/auth-flow-hosted-vs-native.drawio) | [architecture](architecture.md), [auth-ui-integration](../integrate/hosted-and-native-sign-in.md) |
| Auth flow — Device authorization grant (RFC 8628) | [`auth-flow-device-code.drawio`](../diagrams/auth-flow-device-code.drawio) | [architecture](architecture.md), [integration guide](../integrate/integration-guide.md) |
| Auth flow — SAML 2.0: SP-initiated, IdP-initiated, single logout (3 pages) | [`auth-flow-saml.drawio`](../diagrams/auth-flow-saml.drawio) | [architecture](architecture.md), [integration guide](../integrate/integration-guide.md) |
| Deployment — MVP | [`deployment-mvp.drawio`](../diagrams/deployment-mvp.drawio) | [architecture](architecture.md) |
| Deployment — Production | [`deployment-production.drawio`](../diagrams/deployment-production.drawio) | [architecture](architecture.md) |
| Data-layer layering | [`data-layer-layering.drawio`](../diagrams/data-layer-layering.drawio) | [database](../developer/database.md) |
| Tenant isolation (defense in depth) | [`tenant-isolation.drawio`](../diagrams/tenant-isolation.drawio) | [database](../developer/database.md), [architecture](architecture.md) |
| Enterprise capability map | [`enterprise-capability-map.drawio`](../diagrams/enterprise-capability-map.drawio) | [enterprise](enterprise.md) |
| Configuration inheritance | [`config-inheritance.drawio`](../diagrams/config-inheritance.drawio) | [admin-control](../install/admin-control.md) |
| Roles & permissions (multi-role + composite) | [`roles-permissions.drawio`](../diagrams/roles-permissions.drawio) | [data-model](../developer/data-model.md) |

**Schema layout** (platform + per-app schemas) is documented in Mermaid/text in [database](../developer/database.md) and [applications-and-oauth-clients](../integrate/applications-and-oauth-clients.md) — no separate draw.io yet.

The three flow diagrams above (hosted vs native, device, SAML) are generated as sequence diagrams in this palette and can be edited in draw.io like the others.

## Editing notes

- `.drawio` files here are stored as **uncompressed XML** so diffs are reviewable in Git.
- When you change a diagram, also update the matching Mermaid block in the doc (or vice-versa) to keep them in sync.
- Suggested palette: clients `#dae8fc`, UI `#d5e8d4`, auth-server `#ffe6cc`, datastores `#f8cecc`, security/infra `#e1d5e7`.
