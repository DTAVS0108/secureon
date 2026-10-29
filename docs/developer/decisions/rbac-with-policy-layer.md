---
title: RBAC now, ABAC/ReBAC-ready via PolicyEvaluator
order: 5
audience: internal
---

# RBAC now, ABAC/ReBAC-ready via PolicyEvaluator

**Status:** Accepted

## Context
RBAC covers the MVP's authorization needs, but large customers eventually need attribute- or relationship-based access control (ABAC/ReBAC). We want to avoid a future rewrite of every authorization call site.

## Decision
Implement **application-scoped RBAC** now (`permission` → `role` → `user_role`), but route all authorization decisions through a single **`PolicyEvaluator` interface**.

## Rationale
- RBAC is sufficient and simple for the MVP.
- The interface decouples decisions from call sites, so a future ReBAC engine (e.g. **OpenFGA**) is a new implementation, not a refactor.

## Consequences
- Slight upfront indirection (the interface).
- Roles/permissions resolve into JWT claims at token-issuance time.
- Future ABAC/ReBAC adoption is additive.
