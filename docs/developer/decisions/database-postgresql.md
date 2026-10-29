---
title: Database: PostgreSQL only (abstracted for future engines)
order: 2
audience: internal
---

# Database: PostgreSQL only (abstracted for future engines)

**Status:** Accepted (supersedes the earlier "PostgreSQL + MySQL" direction)

## Context
The platform must persist relational IAM data and let operators configure the database connection at install. We initially considered shipping PostgreSQL **and** MySQL together. Supporting two engines doubles the migration/query/test matrix and forces us to avoid Postgres-only features (notably RLS).

## Decision
Support **PostgreSQL only**, via **JPA/Hibernate** + **Flyway**. Keep data access behind **repository interfaces** so another engine could be added later without rewriting domain code, but do not implement multi-engine support now.

## Rationale
- One dialect and one test matrix → simpler, faster, fewer edge cases.
- Unlocks Postgres-native features: **RLS** (tenant-isolation backstop), `jsonb`, strong concurrency, mature managed offerings.
- The repository abstraction preserves optionality without paying the multi-engine cost today.

## Consequences
- NoSQL remains out of scope (relational integrity is core to IAM).
- Tenant isolation can now use RLS in addition to app-layer scoping — see ADR 0003.
- Adding a future engine means implementing adapters for the few Postgres-specific spots (RLS, JSON querying), not a rewrite.
- Conventions (UUIDs, string enums, UTC timestamps) are kept engine-neutral to ease any future port.
