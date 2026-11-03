# Terraform modules (not tested on the clouds yet)

One module per cloud. Each creates SecureOne with its database, Redis and secrets in a network of its own,
runs the published image, and passes SecureOne only the variables it needs (the public address, the database,
Redis, the encryption key, the trusted proxy range). They are **not in the user documentation** until each one
has been applied on a real account.

| Folder | Runs SecureOne on | Database | Redis | Secrets |
|--------|-------------------|----------|-------|---------|
| `aws/` | ECS Fargate behind an Application Load Balancer (https; http redirected) | RDS PostgreSQL 16 | ElastiCache Redis 7 (TLS, password) | Secrets Manager |
| `gcp/` | Cloud Run, 1 instance always on, CPU not throttled, Direct VPC egress | Cloud SQL PostgreSQL 16 (private IP) | Memorystore Redis 7.2 (auth string) | Secret Manager |
| `azure/` | Container Apps in a virtual network, 1 replica always on | PostgreSQL flexible server 16 (private access) | Azure Managed Redis (TLS, private endpoint) | Container app secrets |

Azure uses **Azure Managed Redis**: Azure Cache for Redis stopped accepting new caches (new customers from
April 2026) and retires on 30 September 2028. The Enterprise clustering policy gives one endpoint, as SecureOne
expects.

## Use

```bash
cd deploy/terraform/aws            # or gcp, azure
cp terraform.tfvars.example terraform.tfvars   # fill it in
terraform init
terraform apply
```

Then open the `setup_url` output; the setup code is in the service's log. Back up the encryption key secret named
in the outputs, apart from the database backups. The Terraform state holds every secret: keep it in a protected
remote backend (S3 with encryption, a GCS bucket, an Azure storage account), not on a laptop.

- **AWS** needs an ACM certificate for the host name first; point the host name at `load_balancer_dns_name`.
- **Google Cloud** without `public_base_url` uses the service's `run.app` address. With a domain, map it to the
  service (Cloud Run domain mapping or a load balancer) and set `public_base_url`.
- **Azure** without `public_base_url` uses the container app's own address. With a domain, add it to the
  container app with a managed certificate (`custom_domain_verification_id` is the TXT value) and set
  `public_base_url`.
- `deletion_protection` (AWS, Google Cloud) is on: set it to false and apply before `terraform destroy`.

## Checks without a cloud account

From this folder, with Docker (the same commands work with a local `terraform`):

```bash
for m in aws gcp azure; do
  docker run --rm -v "$PWD/$m:/m" -w /m hashicorp/terraform:1.13.4 init -backend=false
  docker run --rm -v "$PWD/$m:/m" -w /m hashicorp/terraform:1.13.4 validate
  docker run --rm -v "$PWD/$m:/m" -w /m hashicorp/terraform:1.13.4 test
done
```

`terraform test` runs each module against mock providers (`tests/plan.tftest.hcl`) and checks what SecureOne
receives: the JDBC URL with TLS and `currentSchema=platform`, the Redis URL with its password, a 32-byte key,
secrets from the secret store, health checks on `/api/info` port 9000, private databases, one instance always on.
For AWS, a real `terraform plan` also works without an account, using an override file with fake keys and
`skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check`. Google Cloud and Azure
need credentials even to plan.

## To check on the first real apply

- That forwarded client addresses are trusted: in the console, the sessions and the audit log show visitors' own
  addresses, not the proxy's. AWS and Azure use the network range; Google Cloud uses `trusted_proxies`
  (default `169.254.0.0/16`, to be confirmed).
- Start-up time against the health checks (AWS grace period 240 s, Cloud Run and Container Apps startup probes
  about 5 minutes).
- Cloud Run: an organisation policy may forbid `allUsers` as invoker; the sign-in pages must be public.
- Azure Managed Redis: the private endpoint name `redisEnterprise` and the DNS zone `privatelink.redis.azure.net`.
- Update the image version in `variables.tf` and in the tests with each release.
