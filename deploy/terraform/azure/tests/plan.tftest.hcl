# terraform test: the module against a mock Azure (no subscription needed), checking what SecureOne receives.

mock_provider "azurerm" {
  mock_resource "azurerm_container_app_environment" {
    defaults = {
      id             = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.App/managedEnvironments/secureone"
      default_domain = "happy-sea-1234.westeurope.azurecontainerapps.io"
    }
  }
  mock_resource "azurerm_postgresql_flexible_server" {
    defaults = {
      id   = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.DBforPostgreSQL/flexibleServers/secureone"
      fqdn = "secureone.postgres.database.azure.com"
    }
  }
  mock_resource "azurerm_managed_redis" {
    defaults = {
      id               = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.Cache/redisEnterprise/secureone"
      hostname         = "secureone.westeurope.redis.azure.net"
      default_database = { primary_access_key = "mock-key", port = 10000 }
    }
  }
  # Real-looking IDs where the provider checks the format.
  mock_resource "azurerm_resource_group" {
    defaults = { id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone" }
  }
  mock_resource "azurerm_virtual_network" {
    defaults = { id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.Network/virtualNetworks/secureone" }
  }
  mock_resource "azurerm_subnet" {
    defaults = { id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.Network/virtualNetworks/secureone/subnets/apps" }
  }
  mock_resource "azurerm_private_dns_zone" {
    defaults = { id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.Network/privateDnsZones/zone" }
  }
  mock_resource "azurerm_log_analytics_workspace" {
    defaults = { id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/secureone/providers/Microsoft.OperationalInsights/workspaces/secureone" }
  }
}

variables {
  subscription_id = "00000000-0000-0000-0000-000000000000"
  location        = "westeurope"
}

run "plan" {
  command = apply

  assert {
    condition     = local.environment.SECUREONE_PUBLIC_BASE_URL == "https://secureone.happy-sea-1234.westeurope.azurecontainerapps.io"
    error_message = "Without a domain, the public address must be the container app's own address."
  }
  assert {
    condition     = local.environment.SECUREONE_DB_URL == "jdbc:postgresql://secureone.postgres.database.azure.com:5432/secureone?sslmode=require&currentSchema=platform"
    error_message = "Database URL must use TLS and the platform schema."
  }
  assert {
    condition     = local.secrets.SECUREONE_REDIS_URL == "rediss://:mock-key@secureone.westeurope.redis.azure.net:10000"
    error_message = "Redis must be reached over TLS on port 10000 with its key."
  }
  assert {
    condition     = startswith(local.secrets.SECUREONE_ENCRYPTION_KEY, "base64:") && length(random_bytes.encryption_key.base64) == 44
    error_message = "The encryption key must be base64: and 32 bytes."
  }
  assert {
    condition     = azurerm_container_app.main.template[0].min_replicas == 1 && azurerm_container_app.main.ingress[0].target_port == 9000
    error_message = "One replica must always run, with ingress on port 9000."
  }
  assert {
    condition = toset([for e in azurerm_container_app.main.template[0].container[0].env : e.name if e.secret_name != null]) == toset(
    ["SECUREONE_DB_PASSWORD", "SECUREONE_REDIS_URL", "SECUREONE_ENCRYPTION_KEY"])
    error_message = "Secrets must be passed as container app secrets."
  }
  assert {
    condition     = azurerm_postgresql_flexible_server.main.version == "16" && azurerm_postgresql_flexible_server.main.public_network_access_enabled == false
    error_message = "Database must be private PostgreSQL 16."
  }
  assert {
    condition     = azurerm_managed_redis.main.public_network_access == "Disabled" && azurerm_managed_redis.main.default_database[0].clustering_policy == "EnterpriseCluster"
    error_message = "Redis must be private, with a single endpoint."
  }
}

run "refuses_http_address" {
  command = plan
  variables {
    public_base_url = "http://id.example.com"
  }
  expect_failures = [var.public_base_url]
}
