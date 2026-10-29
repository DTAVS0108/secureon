---
title: Frontend: Next.js + TypeScript
order: 4
audience: internal
---

# Frontend: Next.js + TypeScript

**Status:** Accepted

## Context
We need three frontend surfaces: an admin dashboard, hosted login/consent/MFA pages, and a developer docs portal.

## Decision
Use **Next.js + TypeScript + Tailwind + shadcn/ui**, with TanStack Query (server state) and TanStack Table (admin grids).

## Rationale
- One framework cleanly serves all three surfaces (SPA admin, SSR docs, hosted auth pages).
- Largest ecosystem and hiring pool; strong component libraries for admin-heavy UIs.

## Consequences
- React ecosystem churn; we assemble some pieces ourselves.
- Hosted login pages need strict CSP / anti-clickjacking hardening (see Security doc).
