# SecureOne on Cloud Run (always one instance warm, CPU always allocated), Cloud SQL for PostgreSQL 16 and
# Memorystore for Redis on a private network, secrets in Secret Manager.

data "google_project" "this" {}

locals {
  apis = ["run.googleapis.com", "sqladmin.googleapis.com", "redis.googleapis.com", "secretmanager.googleapis.com",
  "servicenetworking.googleapis.com", "compute.googleapis.com"]
  # Cloud Run's own address: https://<service>-<project number>.<region>.run.app
  public_base_url = var.public_base_url != "" ? var.public_base_url : "https://${var.name}-${data.google_project.this.number}.${var.region}.run.app"
  secrets = {
    SECUREONE_DB_PASSWORD    = random_password.db.result
    SECUREONE_REDIS_URL      = "redis://:${google_redis_instance.main.auth_string}@${google_redis_instance.main.host}:${google_redis_instance.main.port}"
    SECUREONE_ENCRYPTION_KEY = "base64:${random_bytes.encryption_key.base64}"
  }
  environment = {
    SECUREONE_PUBLIC_BASE_URL = local.public_base_url
    SECUREONE_DB_URL          = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/secureone?sslmode=require&currentSchema=platform"
    SECUREONE_DB_USERNAME     = google_sql_user.main.name
    SECUREONE_TRUSTED_PROXIES = var.trusted_proxies
  }
}

resource "google_project_service" "apis" {
  for_each           = toset(local.apis)
  service            = each.value
  disable_on_destroy = false
}

# --- Network: a VPC for Cloud Run's egress, peered with Google's services (Cloud SQL, Memorystore).

resource "google_compute_network" "main" {
  name                    = var.name
  auto_create_subnetworks = false
  depends_on              = [google_project_service.apis]
}

resource "google_compute_subnetwork" "run" {
  name                     = "${var.name}-run"
  network                  = google_compute_network.main.id
  region                   = var.region
  ip_cidr_range            = var.network_cidr
  private_ip_google_access = true
}

resource "google_compute_global_address" "services" {
  name          = "${var.name}-services"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "services" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.services.name]
}

# --- Data

resource "random_password" "db" {
  length  = 32
  special = false
}

# 32 random bytes: SecureOne's encryption key, kept outside the database.
resource "random_bytes" "encryption_key" {
  length = 32
}

resource "google_sql_database_instance" "main" {
  name                = var.name
  database_version    = "POSTGRES_16"
  region              = var.region
  deletion_protection = var.deletion_protection
  settings {
    tier              = var.db_tier
    edition           = "ENTERPRISE"
    availability_type = var.db_high_availability ? "REGIONAL" : "ZONAL"
    disk_autoresize   = true
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }
    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }
  }
  depends_on = [google_service_networking_connection.services]
}

resource "google_sql_database" "main" {
  name     = "secureone"
  instance = google_sql_database_instance.main.name
}

# Cloud SQL users belong to cloudsqlsuperuser, which may create the secureone_app role on the first start.
resource "google_sql_user" "main" {
  name     = "secureone"
  instance = google_sql_database_instance.main.name
  password = random_password.db.result
}

resource "google_redis_instance" "main" {
  name                    = var.name
  tier                    = "BASIC"
  memory_size_gb          = var.redis_memory_gb
  region                  = var.region
  redis_version           = "REDIS_7_2"
  authorized_network      = google_compute_network.main.id
  connect_mode            = "PRIVATE_SERVICE_ACCESS"
  auth_enabled            = true
  transit_encryption_mode = "DISABLED"
  depends_on              = [google_service_networking_connection.services]
}

# --- Secrets (back up SECUREONE_ENCRYPTION_KEY: without it, a database backup cannot be used)

resource "google_secret_manager_secret" "main" {
  for_each  = toset(keys(local.secrets))
  secret_id = "${var.name}-${lower(replace(each.value, "_", "-"))}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "main" {
  for_each    = google_secret_manager_secret.main
  secret      = each.value.id
  secret_data = local.secrets[each.key]
}

resource "google_service_account" "run" {
  account_id   = "${var.name}-run"
  display_name = "SecureOne on Cloud Run"
}

resource "google_secret_manager_secret_iam_member" "run" {
  for_each  = google_secret_manager_secret.main
  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.run.email}"
}

# --- The service

resource "google_cloud_run_v2_service" "main" {
  name                = var.name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = var.deletion_protection

  template {
    service_account = google_service_account.run.email
    scaling {
      min_instance_count = 1
      max_instance_count = var.max_instances
    }
    vpc_access {
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.run.id
      }
      egress = "PRIVATE_RANGES_ONLY"
    }
    containers {
      image = var.image
      ports {
        container_port = 9000
      }
      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
        # CPU stays allocated between requests (background jobs, sessions), no throttling.
        cpu_idle          = false
        startup_cpu_boost = true
      }
      dynamic "env" {
        for_each = local.environment
        content {
          name  = env.key
          value = env.value
        }
      }
      dynamic "env" {
        for_each = google_secret_manager_secret.main
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value.secret_id
              version = "latest"
            }
          }
        }
      }
      startup_probe {
        http_get {
          path = "/api/info"
          port = 9000
        }
        initial_delay_seconds = 20
        period_seconds        = 10
        timeout_seconds       = 5
        failure_threshold     = 24
      }
      liveness_probe {
        http_get {
          path = "/api/info"
          port = 9000
        }
        period_seconds    = 30
        failure_threshold = 3
      }
    }
  }

  depends_on = [google_secret_manager_secret_version.main, google_secret_manager_secret_iam_member.run]
}

# Public sign-in pages: anyone may call the service.
resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.main.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
