# terraform test: plans the module against a mock AWS (no account needed) and checks what SecureOne receives.

mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  # Real-looking ARNs where the provider checks the format.
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/secureone-execution" }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:loadbalancer/app/secureone/0123456789abcdef" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:targetgroup/secureone/0123456789abcdef" }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:secureone/x-AbCdEf" }
  }
  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:eu-west-1:123456789012:task-definition/secureone:1" }
  }
  mock_resource "aws_db_instance" {
    defaults = { address = "secureone.abc123.eu-west-1.rds.amazonaws.com", port = 5432 }
  }
  mock_resource "aws_elasticache_replication_group" {
    defaults = { primary_endpoint_address = "master.secureone.abc123.euw1.cache.amazonaws.com" }
  }
}

variables {
  region             = "eu-west-1"
  availability_zones = ["eu-west-1a", "eu-west-1b"]
  public_base_url    = "https://id.example.com"
  certificate_arn    = "arn:aws:acm:eu-west-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
}

run "plan" {
  command = apply

  assert {
    condition     = jsondecode(aws_ecs_task_definition.main.container_definitions)[0].image == "dmtavs0108/secureone:0.1.0"
    error_message = "Wrong image."
  }
  assert {
    condition = alltrue([
      for name in ["SECUREONE_PUBLIC_BASE_URL", "SECUREONE_DB_URL", "SECUREONE_DB_USERNAME", "SECUREONE_TRUSTED_PROXIES"] :
      contains([for e in jsondecode(aws_ecs_task_definition.main.container_definitions)[0].environment : e.name], name)
    ])
    error_message = "A required setting is missing from the environment."
  }
  assert {
    condition = toset([for s in jsondecode(aws_ecs_task_definition.main.container_definitions)[0].secrets : s.name]) == toset(
    ["SECUREONE_DB_PASSWORD", "SECUREONE_REDIS_URL", "SECUREONE_ENCRYPTION_KEY"])
    error_message = "Secrets must come from Secrets Manager."
  }
  assert {
    condition     = can(regex("^jdbc:postgresql://.+/secureone\\?sslmode=require&currentSchema=platform$", local.environment.SECUREONE_DB_URL))
    error_message = "Database URL must use TLS and the platform schema."
  }
  assert {
    condition     = startswith(aws_secretsmanager_secret_version.encryption_key.secret_string, "base64:") && length(random_bytes.encryption_key.base64) == 44
    error_message = "The encryption key must be base64: and 32 bytes."
  }
  assert {
    condition     = startswith(aws_secretsmanager_secret_version.redis_url.secret_string, "rediss://:")
    error_message = "Redis must be reached over TLS with its password."
  }
  assert {
    condition     = aws_lb_target_group.main.health_check[0].path == "/api/info" && aws_lb_target_group.main.port == 9000
    error_message = "Health check must use /api/info on port 9000."
  }
  assert {
    condition     = aws_db_instance.main.engine_version == "16" && aws_db_instance.main.publicly_accessible == false && aws_db_instance.main.storage_encrypted
    error_message = "Database must be private, encrypted PostgreSQL 16."
  }
  assert {
    condition     = aws_ecs_service.main.network_configuration[0].assign_public_ip == false
    error_message = "Tasks must stay in the private subnets."
  }
}

run "refuses_http_address" {
  command = plan
  variables {
    public_base_url = "http://id.example.com"
  }
  expect_failures = [var.public_base_url]
}

run "refuses_small_memory" {
  command = plan
  variables {
    task_memory = 1024
  }
  expect_failures = [var.task_memory]
}
