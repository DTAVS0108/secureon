---
title: Install on Kubernetes
summary: Install SecureOne on any Kubernetes cluster with its Helm chart
order: 2
audience: public
---

# Install on Kubernetes

The SecureOne Helm chart runs the same image as Docker, `dmtavs0108/secureone:1.1.0`, on any Kubernetes cluster
(EKS, AKS, GKE, k3s, on-premises). It works on Intel/AMD and ARM nodes.

## What you need

- Kubernetes 1.27 or later, `kubectl` and **Helm 3**
- **PostgreSQL 16+** (an empty database and a user that owns it) and **Redis 7+** that the cluster can reach,
  for example your cloud's managed services
- An ingress controller with a TLS certificate for your address, for example `id.example.com`

## 1. Add the chart repository

```bash
helm repo add secureone https://secureone-website.vercel.app/charts
helm repo update
```

## 2. Write your values

Save as `secureone-values.yaml`:

```yaml
publicUrl: https://id.example.com

database:
  url: jdbc:postgresql://postgres.example.internal:5432/secureone?currentSchema=platform
  username: secureone
  password: choose-a-database-password

redis:
  url: redis://redis.example.internal:6379

ingress:
  enabled: true
  className: nginx
  host: id.example.com
  tlsSecretName: secureone-tls      # your certificate
```

## 3. Install

```bash
helm install secureone secureone/secureone -n secureone --create-namespace -f secureone-values.yaml
kubectl -n secureone rollout status deploy/secureone
```

The chart:

- creates the Secret `secureone-secrets` once, with a random **encryption key** and your database password;
- runs a job that creates (and on later upgrades, upgrades) the database tables, then starts two replicas;
- serves everything on your one address: sign-in pages, APIs and the admin console at `/admin`.

SecureOne creates its token signing key and code pepper on the first start and stores them encrypted in the
database, so every replica uses the same ones.

## 4. Back up the encryption key

```bash
kubectl -n secureone get secret secureone-secrets -o jsonpath='{.data.SECUREONE_ENCRYPTION_KEY}' | base64 -d
```

Store it in your password manager or secret store, **apart from the database backups**. Without it, a database
backup cannot be restored. The chart keeps this Secret on upgrades and even on `helm uninstall`.

## 5. Finish setup in the browser

Read the setup code from the log, then open `https://id.example.com/admin/setup`:

```bash
kubectl -n secureone logs deploy/secureone | grep -A2 "not set up yet"
```

The setup wizard checks the installation, sets up email (optional) and creates the first platform operator. The
key backup step is skipped on Kubernetes: you backed the key up in step 4.

## Your own Secret

To manage secrets yourself (for example with External Secrets or Sealed Secrets), create a Secret with any
`SECUREONE_*` variables, at least `SECUREONE_ENCRYPTION_KEY` (`base64:` followed by 32 random bytes in base64) and
`SECUREONE_DB_PASSWORD`, and name it in the values:

```yaml
existingSecret: my-secureone-secret
database:
  password: ""          # comes from the Secret
```

Settings in the values win over the same names in the Secret; set them to `""` when the Secret provides them.

## Upgrade

```bash
helm repo update
helm upgrade secureone secureone/secureone -n secureone -f secureone-values.yaml
```

Back up the database first. The upgrade job runs before the new pods start; if it fails, the old pods keep
running. See [Configuration as code and upgrades](configuration-and-upgrades.md).

## Settings

| Value | Default | Meaning |
|---|---|---|
| `publicUrl` | `https://id.example.com` | Your address (https); also the token issuer |
| `database.url`, `.username`, `.password` | | Your PostgreSQL |
| `redis.url` | `redis://redis:6379` | Your Redis |
| `existingSecret` | `""` | Your own Secret instead of the generated one |
| `replicaCount` | `2` | SecureOne pods |
| `image.tag` | the chart's version | Pin a version; never `latest` in production |
| `ingress.enabled`, `.className`, `.host`, `.tlsSecretName` | off | Ingress for your address |
| `env` | `SECUREONE_TRUSTED_PROXIES` | Any other setting from [Deployment](deployment.md#all-settings) |
| `migrationJob.enabled` | `true` | Upgrade the database in a job before the pods start |
| `resources` | 768 MiB to 1.5 GiB memory | Per pod |

The health, info and metrics endpoints are on port `9001` inside the cluster (`/actuator/health`,
`/actuator/prometheus`), never on the ingress.

## Remove

```bash
helm uninstall secureone -n secureone
```

The database and the Secret `secureone-secrets` stay. Delete the Secret yourself only when you no longer need the
database: `kubectl -n secureone delete secret secureone-secrets`.
