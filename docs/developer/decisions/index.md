---
title: Architecture decisions
summary: Why SecureOne is built the way it is
order: 6
audience: internal
---

# Architecture decisions

This folder captures **why** key decisions were made, so future contributors don't have to re-litigate them. Each ADR is short and immutable once accepted; superseded decisions get a new ADR that references the old one.

## Format
Each ADR: Context → Decision → Consequences → Status.

## Index

| ID | Decision | Status |
|---|---|---|
| [0001](backend-spring-authorization-server.md) | Backend: Spring Boot + Spring Authorization Server | Accepted |
| [0002](database-postgresql.md) | Database: PostgreSQL only (abstracted for future engines) | Accepted |
| [0003](tenant-isolation.md) | Tenant isolation: layered (app-layer + PostgreSQL RLS) | Accepted |
| [0004](frontend-nextjs.md) | Frontend: Next.js + TypeScript | Accepted |
| [0005](rbac-with-policy-layer.md) | RBAC now, ABAC/ReBAC-ready via PolicyEvaluator | Accepted |
| [0006](passkey-first-mfa.md) | Passkey-first, phishing-resistant MFA | Accepted |
| [0007](enterprise-scope-phasing.md) | Enterprise capabilities are phased, not in the MVP | Accepted |
| [0008](admin-control-layered-config.md) | Admin control via admin scopes + layered, lockable config | Accepted |
| [0009](composite-hierarchical-roles.md) | Composite / hierarchical roles | Accepted |
| [0010](platform-and-app-schemas.md) | Platform schema + per-application PostgreSQL schemas | Accepted |
