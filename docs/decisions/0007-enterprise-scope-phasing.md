# ADR 0007 — Enterprise capabilities are phased, not in the MVP

**Status:** Accepted

## Context
SecureOne aims to be a full enterprise IAM platform (federation/brokering, IGA, fine-grained authz, adaptive auth, compliance, multi-region scale). Building all of that upfront would stall the MVP and add risk.

## Decision
Document the full enterprise capability set in [Enterprise Capabilities](../10-enterprise.md) but **keep it out of the MVP**. Sequence it across Phases 2–4. Build the MVP on abstractions (isolated auth core, `PolicyEvaluator`, repository layer, pluggable tenant resolution, append-only audit) so enterprise features are **additive**.

## Rationale
- Ship a usable, secure OIDC provider fast; expand breadth over time.
- The abstractions chosen in earlier ADRs make later additions non-breaking.
- A written, phased capability map prevents scope creep while preserving the long-term vision.

## Consequences
- Enterprise data-model additions (organizations, groups, IGA, policies, webhooks, branding) are listed as future tables, not built yet.
- Each enterprise domain can be delivered as an independent, feature-flagged module.
