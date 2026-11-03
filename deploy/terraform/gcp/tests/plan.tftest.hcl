# terraform test: the module against a mock Google Cloud (no account needed), checking what SecureOne receives.

mock_provider "google" {
  mock_data "google_project" {
    defaults = { number = "123456789012" }
  }
  mock_resource "google_compute_network" {
    defaults = { id = "projects/my-project/global/networks/secureone" }
  }
  mock_resource "google_sql_database_instance" {
    defaults = { private_ip_address = "10.20.0.3" }
  }
  mock_resource "google_redis_instance" {
    defaults = { host = "10.20.1.4", port = 6379, auth_string = "mock-auth" }
  }
  mock_resource "google_service_account" {
    defaults = { email = "secureone-run@project.iam.gserviceaccount.com" }
  }
}

variables {
  project_id = "my-project"
  region     = "europe-west1"
}

run "plan" {
  command = apply

  assert {
    condition     = local.environment.SECUREONE_PUBLIC_BASE_URL == "https://secureone-123456789012.europe-west1.run.app"
    error_message = "Without a domain, the public address must be the run.app address."
  }
  assert {
    condition     = local.environment.SECUREONE_DB_URL == "jdbc:postgresql://10.20.0.3:5432/secureone?sslmode=require&currentSchema=platform"
    error_message = "Database URL must use the private address, TLS and the platform schema."
  }
  assert {
    condition     = local.secrets.SECUREONE_REDIS_URL == "redis://:mock-auth@10.20.1.4:6379"
    error_message = "Redis URL must carry the auth string."
  }
  assert {
    condition     = startswith(local.secrets.SECUREONE_ENCRYPTION_KEY, "base64:") && length(random_bytes.encryption_key.base64) == 44
    error_message = "The encryption key must be base64: and 32 bytes."
  }
  assert {
    condition     = google_cloud_run_v2_service.main.template[0].scaling[0].min_instance_count == 1
    error_message = "One instance must always run."
  }
  assert {
    condition     = google_cloud_run_v2_service.main.template[0].containers[0].resources[0].cpu_idle == false
    error_message = "CPU must not be throttled between requests."
  }
  assert {
    condition = toset([for e in google_cloud_run_v2_service.main.template[0].containers[0].env : e.name if length(e.value_source) > 0]) == toset(
    ["SECUREONE_DB_PASSWORD", "SECUREONE_REDIS_URL", "SECUREONE_ENCRYPTION_KEY"])
    error_message = "Secrets must come from Secret Manager."
  }
  assert {
    condition     = google_sql_database_instance.main.settings[0].ip_configuration[0].ipv4_enabled == false
    error_message = "The database must have no public address."
  }
  assert {
    condition     = google_cloud_run_v2_service.main.template[0].containers[0].startup_probe[0].http_get[0].path == "/api/info"
    error_message = "Startup probe must use /api/info."
  }
}

run "own_domain" {
  command = apply
  variables {
    public_base_url = "https://id.example.com"
  }
  assert {
    condition     = local.environment.SECUREONE_PUBLIC_BASE_URL == "https://id.example.com"
    error_message = "The given domain must be used."
  }
}

run "refuses_http_address" {
  command = plan
  variables {
    public_base_url = "http://id.example.com"
  }
  expect_failures = [var.public_base_url]
}
