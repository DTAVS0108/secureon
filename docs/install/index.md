---
title: Install and operate
summary: Install SecureOne from its image, configure it, upgrade it and keep it running
order: 2
audience: public
---

# Install and operate

SecureOne ships as **one container image**, `dmtavs0108/secureone:1.1.1`, for Intel/AMD (x86-64) machines. It needs
PostgreSQL 16 or later and Redis 7 or later. You start it with a few variables; it creates its own secrets and you
finish setup in the browser.

| Task | Read |
|---|---|
<!-- not on website -->
| Try it on one machine | [Quickstart](../get-started/quickstart.md) |
<!-- /not on website -->
| Install it with Docker or Docker Desktop | [Installation](installation.md) |
| Install it on Kubernetes (Helm) | [Install on Kubernetes](kubernetes.md) |
| Every setting: secrets, TLS, several instances, backups | [Deployment](deployment.md) |
| Keep tenants in files, upgrade and roll back | [Configuration as code and upgrades](configuration-and-upgrades.md) |
| Step-by-step procedures | [Runbooks](runbooks/index.md) |
<!-- not on website -->
| How settings combine across levels | [Settings governance](settings-governance.md), [Admin control](admin-control.md) |
<!-- /not on website -->
<!-- website only -->
| How settings combine across levels | [Settings governance](settings-governance.md) |
<!-- /website only -->

<!-- developer -->
For developers: [Local development setup](../developer/local-setup.md) (running from source on your machine) and
[Environment and operations](environment-and-operations.md) (development stack, schedulers, troubleshooting).
<!-- /developer -->
