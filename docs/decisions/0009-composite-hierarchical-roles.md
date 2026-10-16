# ADR 0009 — Composite / hierarchical roles

**Status:** Accepted

## Context
Flat roles (role = set of permissions) cover basic needs, but enterprises want **role hierarchy** — a role that includes other roles (e.g. `super-editor` grants `editor` + `reviewer`) — to avoid duplicating permission sets and to model org structures. The industry term is *composite roles*.

## Decision
Support **composite roles** via a self-referential join `role_composite (parent_role_id, child_role_id)` plus an `is_composite` flag on `role`. A principal's **effective permissions** are the transitive union of: directly assigned roles + all child roles (recursively) + group-derived roles (Phase 3). Resolution happens at token-issuance time, behind the `PolicyEvaluator`, and is cached.

## Rationale
- Eliminates permission duplication; mirrors real org/role structures.
- Self-join keeps the schema simple and engine-portable.
- `PolicyEvaluator` already centralizes checks, so flattening the hierarchy is an implementation detail callers never see.

## Consequences
- Must **prevent cycles** (reject an edge that would make a role its own ancestor) and bound recursion depth; resolution is memoized for performance.
- Effective-permission caches must be invalidated when `role_composite` or `role_permission` change.
- Stays fully compatible with the future ABAC/ReBAC direction (ADR 0005).
