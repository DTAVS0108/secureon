---
title: Install and operate
summary: Install SecureOne from its image, configure it, upgrade it and keep it running
order: 2
audience: public
---

# Install and operate

SecureOne ships as **one container image**, `dmtavs0108/secureone:1.0.0`. It needs PostgreSQL 16 or later and
Redis 7 or later, and every setting is read when the container starts.

| Task | Read |
|---|---|
| Try it on one machine | [Quickstart](../get-started/quickstart.md) |
| Run it for real: image, settings, secrets, backups | [Deployment](deployment.md) |
| Keep tenants in files, upgrade and roll back | [Configuration as code and upgrades](configuration-and-upgrades.md) |
| Step-by-step procedures (signed-in operators) | [Runbooks](runbooks/index.md) |
| How settings combine across levels (signed-in operators) | [Settings governance](settings-governance.md), [Admin control](admin-control.md) |

<!-- developer -->
For developers: [Installation](installation.md) (running from source on your machine) and
[Environment and operations](environment-and-operations.md) (development stack, schedulers, troubleshooting).
<!-- /developer -->
