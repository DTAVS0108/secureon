# Diagrams

Every architecture/flow diagram exists in two forms:

- **Mermaid** — embedded inline in the relevant doc; renders automatically on GitHub.
- **`.drawio`** — editable source in this folder; **view inline in SecureOne Confluence** on the parent doc page (e.g. [Architecture](../03-architecture.md) → `/confluence/architecture`), or edit in [draw.io / diagrams.net](https://app.diagrams.net) / VS Code Draw.io extension.

## Viewing in SecureOne Confluence

1. Open **SecureOne Confluence** from the admin sidebar (new tab) → http://localhost:9000/admin/confluence
2. Go to the doc listed in **Used in** below (or open **Diagrams** and click a row — links to that doc).
3. Scroll to the section with **Editable source** — the draw.io viewer is embedded inline below the link.
4. Download the `.drawio` source from the link or `GET /api/confluence/assets/diagrams/{filename}.drawio` (session required).

> Legacy `/confluence/diagram/diagrams/{file}.drawio` URLs redirect to the parent doc page.

## Index

| Diagram | Source | Used in |
|---|---|---|
| Logical architecture | [`logical-architecture.drawio`](logical-architecture.drawio) | [03-architecture](../03-architecture.md) |
| Auth flow — Authorization Code + PKCE | [`auth-flow-authorization-code-pkce.drawio`](auth-flow-authorization-code-pkce.drawio) | [03-architecture](../03-architecture.md) |
| Auth flow — Client Credentials (M2M) | [`auth-flow-client-credentials.drawio`](auth-flow-client-credentials.drawio) | [03-architecture](../03-architecture.md) |
| Auth flow — Refresh token rotation | [`auth-flow-refresh-rotation.drawio`](auth-flow-refresh-rotation.drawio) | [03-architecture](../03-architecture.md) |
| Auth flow — Hosted vs native sign-in (2 pages) | [`auth-flow-hosted-vs-native.drawio`](auth-flow-hosted-vs-native.drawio) | [03-architecture](../03-architecture.md), [13-auth-ui-integration](../13-auth-ui-integration.md) |
| Auth flow — Device authorization grant (RFC 8628) | [`auth-flow-device-code.drawio`](auth-flow-device-code.drawio) | [03-architecture](../03-architecture.md), [23-integration guide](../23-application-integration-guide.md) |
| Auth flow — SAML 2.0: SP-initiated, IdP-initiated, single logout (3 pages) | [`auth-flow-saml.drawio`](auth-flow-saml.drawio) | [03-architecture](../03-architecture.md), [23-integration guide](../23-application-integration-guide.md) |
| Deployment — MVP | [`deployment-mvp.drawio`](deployment-mvp.drawio) | [03-architecture](../03-architecture.md) |
| Deployment — Production | [`deployment-production.drawio`](deployment-production.drawio) | [03-architecture](../03-architecture.md) |
| Data-layer layering | [`data-layer-layering.drawio`](data-layer-layering.drawio) | [06-database](../06-database.md) |
| Tenant isolation (defense in depth) | [`tenant-isolation.drawio`](tenant-isolation.drawio) | [06-database](../06-database.md), [03-architecture](../03-architecture.md) |
| Enterprise capability map | [`enterprise-capability-map.drawio`](enterprise-capability-map.drawio) | [10-enterprise](../10-enterprise.md) |
| Configuration inheritance | [`config-inheritance.drawio`](config-inheritance.drawio) | [11-admin-control](../11-admin-control.md) |
| Roles & permissions (multi-role + composite) | [`roles-permissions.drawio`](roles-permissions.drawio) | [04-data-model](../04-data-model.md) |

**Schema layout** (platform + per-app schemas) is documented in Mermaid/text in [06-database](../06-database.md) and [15-applications-and-oauth-clients](../15-applications-and-oauth-clients.md) — no separate draw.io yet.

The three flow diagrams above (hosted vs native, device, SAML) are generated as sequence diagrams in this palette and can be edited in draw.io like the others.

## Editing notes

- `.drawio` files here are stored as **uncompressed XML** so diffs are reviewable in Git.
- When you change a diagram, also update the matching Mermaid block in the doc (or vice-versa) to keep them in sync.
- Suggested palette: clients `#dae8fc`, UI `#d5e8d4`, auth-server `#ffe6cc`, datastores `#f8cecc`, security/infra `#e1d5e7`.
