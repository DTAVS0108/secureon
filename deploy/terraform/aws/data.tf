# PostgreSQL 16 (RDS), Redis 7 (ElastiCache, TLS and a password) and the three secrets SecureOne reads.

resource "random_password" "db" {
  length = 32
  # RDS refuses / @ " and spaces.
  override_special = "!#%^&*()-_=+[]{}<>:?"
}

resource "random_password" "cache" {
  length  = 48
  special = false
}

# 32 random bytes: SecureOne's encryption key, kept outside the database.
resource "random_bytes" "encryption_key" {
  length = 32
}

resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier                 = var.name
  engine                     = "postgres"
  engine_version             = "16"
  instance_class             = var.db_instance_class
  allocated_storage          = var.db_allocated_storage
  storage_type               = "gp3"
  storage_encrypted          = true
  db_name                    = "secureone"
  username                   = "secureone"
  password                   = random_password.db.result
  db_subnet_group_name       = aws_db_subnet_group.main.name
  vpc_security_group_ids     = [aws_security_group.db.id]
  publicly_accessible        = false
  multi_az                   = var.db_multi_az
  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.name}-final"
}

resource "aws_elasticache_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = var.name
  description                = "SecureOne sessions and rate limits"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.cache_node_type
  num_cache_clusters         = 1
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.cache.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.cache.result
  automatic_failover_enabled = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${var.name}/db-password"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "redis_url" {
  name                    = "${var.name}/redis-url"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis_url" {
  secret_id     = aws_secretsmanager_secret.redis_url.id
  secret_string = "rediss://:${random_password.cache.result}@${aws_elasticache_replication_group.main.primary_endpoint_address}:6379"
}

# Back this one up (aws secretsmanager get-secret-value): without it, a database backup cannot be used.
resource "aws_secretsmanager_secret" "encryption_key" {
  name                    = "${var.name}/encryption-key"
  recovery_window_in_days = 30
}

resource "aws_secretsmanager_secret_version" "encryption_key" {
  secret_id     = aws_secretsmanager_secret.encryption_key.id
  secret_string = "base64:${random_bytes.encryption_key.base64}"
}
