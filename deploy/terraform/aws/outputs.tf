output "load_balancer_dns_name" {
  description = "Point the public_base_url host name here (a CNAME, or a Route 53 alias)."
  value       = aws_lb.main.dns_name
}

output "setup_url" {
  description = "Open this once DNS points at the load balancer; the setup code is in the task log."
  value       = "${var.public_base_url}/admin/setup"
}

output "log_group" {
  description = "CloudWatch log group of the SecureOne tasks."
  value       = aws_cloudwatch_log_group.main.name
}

output "encryption_key_secret_arn" {
  description = "Back up this secret's value, apart from the database backups."
  value       = aws_secretsmanager_secret.encryption_key.arn
}
