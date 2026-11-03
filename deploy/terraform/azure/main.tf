# SecureOne on Azure Container Apps (one replica always running) with Azure Database for PostgreSQL flexible
# server 16 and Azure Managed Redis, both reachable only from the app's virtual network.
# Azure Managed Redis replaces Azure Cache for Redis, which no longer accepts new caches.

resource "azurerm_resource_group" "main" {
  name     = var.name
  location = var.location
}

locals {
  # The container app's own address: https://<app name>.<environment default domain>
  public_base_url = var.public_base_url != "" ? var.public_base_url : "https://${var.name}.${azurerm_container_app_environment.main.default_domain}"
  secrets = {
    SECUREONE_DB_PASSWORD    = random_password.db.result
    SECUREONE_REDIS_URL      = "rediss://:${azurerm_managed_redis.main.default_database[0].primary_access_key}@${azurerm_managed_redis.main.hostname}:10000"
    SECUREONE_ENCRYPTION_KEY = "base64:${random_bytes.encryption_key.base64}"
  }
  environment = {
    SECUREONE_PUBLIC_BASE_URL = local.public_base_url
    SECUREONE_DB_URL          = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/secureone?sslmode=require&currentSchema=platform"
    SECUREONE_DB_USERNAME     = azurerm_postgresql_flexible_server.main.administrator_login
    SECUREONE_TRUSTED_PROXIES = var.vnet_cidr
  }
}

# --- Network

resource "azurerm_virtual_network" "main" {
  name                = var.name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = [var.vnet_cidr]
}

resource "azurerm_subnet" "apps" {
  name                 = "apps"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 7, 0)]
  delegation {
    name = "container-apps"
    service_delegation {
      name    = "Microsoft.App/environments"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "db" {
  name                 = "db"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 8, 2)]
  delegation {
    name = "postgres"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "endpoints" {
  name                 = "endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 8, 3)]
}

resource "azurerm_private_dns_zone" "postgres" {
  name                = "${var.name}.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.main.name
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "postgres"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

resource "azurerm_private_dns_zone" "redis" {
  name                = "privatelink.redis.azure.net"
  resource_group_name = azurerm_resource_group.main.name
}

resource "azurerm_private_dns_zone_virtual_network_link" "redis" {
  name                  = "redis"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.redis.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

# --- Data

resource "random_password" "db" {
  length  = 32
  special = false
}

# 32 random bytes: SecureOne's encryption key, kept outside the database. It is stored in this Terraform
# state and as a container app secret: back it up, apart from the database backups.
resource "random_bytes" "encryption_key" {
  length = 32
}

# The administrator belongs to azure_pg_admin, which may create the secureone_app role on the first start.
resource "azurerm_postgresql_flexible_server" "main" {
  name                          = var.name
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  version                       = "16"
  sku_name                      = var.db_sku
  storage_mb                    = var.db_storage_mb
  delegated_subnet_id           = azurerm_subnet.db.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false
  administrator_login           = "secureone"
  administrator_password        = random_password.db.result
  backup_retention_days         = 7
  depends_on                    = [azurerm_private_dns_zone_virtual_network_link.postgres]
  lifecycle {
    # Azure picks the zone; later plans must not try to move the server.
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "main" {
  name      = "secureone"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_managed_redis" "main" {
  name                  = var.name
  resource_group_name   = azurerm_resource_group.main.name
  location              = azurerm_resource_group.main.location
  sku_name              = var.redis_sku
  public_network_access = "Disabled"
  default_database {
    # One endpoint, as for a single Redis server.
    clustering_policy                  = "EnterpriseCluster"
    client_protocol                    = "Encrypted"
    access_keys_authentication_enabled = true
    eviction_policy                    = "NoEviction"
  }
}

resource "azurerm_private_endpoint" "redis" {
  name                = "${var.name}-redis"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.endpoints.id
  private_service_connection {
    name                           = "redis"
    private_connection_resource_id = azurerm_managed_redis.main.id
    subresource_names              = ["redisEnterprise"]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "redis"
    private_dns_zone_ids = [azurerm_private_dns_zone.redis.id]
  }
}

# --- The app

resource "azurerm_log_analytics_workspace" "main" {
  name                = var.name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days
}

resource "azurerm_container_app_environment" "main" {
  name                       = var.name
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  infrastructure_subnet_id   = azurerm_subnet.apps.id
  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}

resource "azurerm_container_app" "main" {
  name                         = var.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"

  dynamic "secret" {
    for_each = local.secrets
    content {
      name  = lower(replace(secret.key, "_", "-"))
      value = secret.value
    }
  }

  ingress {
    external_enabled = true
    target_port      = 9000
    transport        = "http"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = var.max_replicas
    container {
      name   = "secureone"
      image  = var.image
      cpu    = 1
      memory = "2Gi"
      dynamic "env" {
        for_each = local.environment
        content {
          name  = env.key
          value = env.value
        }
      }
      dynamic "env" {
        for_each = local.secrets
        content {
          name        = env.key
          secret_name = lower(replace(env.key, "_", "-"))
        }
      }
      startup_probe {
        transport               = "HTTP"
        port                    = 9000
        path                    = "/api/info"
        initial_delay           = 30
        interval_seconds        = 10
        failure_count_threshold = 30
      }
      liveness_probe {
        transport               = "HTTP"
        port                    = 9000
        path                    = "/api/info"
        interval_seconds        = 30
        failure_count_threshold = 3
      }
    }
  }

  depends_on = [azurerm_postgresql_flexible_server_database.main, azurerm_private_endpoint.redis]
}
