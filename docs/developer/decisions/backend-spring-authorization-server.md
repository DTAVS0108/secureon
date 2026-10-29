---
title: Backend: Spring Boot + Spring Authorization Server
order: 1
audience: internal
---

# Backend: Spring Boot + Spring Authorization Server

**Status:** Accepted

## Context
We need a backend for an IAM platform. The OAuth2/OIDC protocol surface is security-critical and must not be hand-rolled. Candidates: NestJS (`panva/oidc-provider`), Spring Boot (Spring Authorization Server), Go (`fosite`), Python (`Authlib`).

## Decision
Use **Java/Kotlin + Spring Boot + Spring Authorization Server**.

## Rationale
- First-class, well-maintained OAuth2/OIDC authorization server with strong enterprise/security pedigree.
- Mature ecosystem for SAML, LDAP, and enterprise integration (needed for Phase 3).
- Customizable persistence interfaces (`RegisteredClientRepository`, `OAuth2AuthorizationService`) we make tenant-aware.

## Consequences
- Heavier runtime/memory and slower iteration than Node.
- The auth core is kept isolated so the engine could be swapped if ever needed.
- Team must be comfortable with the JVM/Spring ecosystem.
