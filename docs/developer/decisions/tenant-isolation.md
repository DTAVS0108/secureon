---
title: Tenant isolation: layered (app-layer + PostgreSQL RLS)
order: 3
audience: internal
---

# Tenant isolation: layered (app-layer + PostgreSQL RLS)

**Status:** Accepted

## Context
We run on PostgreSQL only (ADR 0002), which provides Row-Level Security (RLS). Cross-tenant data leakage is the highest-risk bug class in a multi-tenant IAM, so we want isolation enforced in more than one place.

## Decision
Enforce tenant isolation in layers (defense in depth):
1. Mandatory `tenant_id` on every tenant-scoped table.
2. Hibernate `@Filter` auto-enabled per request from the authenticated principal's tenant.
3. **PostgreSQL RLS** policies keyed on a per-connection `app.tenant_id` setting — a database-enforced backstop.
4. Automated cross-tenant isolation tests in CI.

Tenant-resolution strategy is pluggable to allow schema-per-tenant / database-per-tenant later.

## Rationale
- App-layer filtering is convenient and centralized; RLS guarantees isolation even if app code has a bug.
- Together they make cross-tenant leakage require *two* independent failures.

## Consequences
- The per-connection tenant setting must be managed carefully with connection pooling (session-level pooling, or set/reset within the transaction) so it doesn't leak across pooled connections.
- Slightly more setup (RLS policies in migrations), repaid by stronger guarantees.
