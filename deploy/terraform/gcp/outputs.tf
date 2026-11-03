output "service_url" {
  description = "Cloud Run's own address of the service."
  value       = google_cloud_run_v2_service.main.uri
}

output "setup_url" {
  description = "Open this to finish the setup; the setup code is in the service log."
  value       = "${local.public_base_url}/admin/setup"
}

output "encryption_key_secret" {
  description = "Back up this secret's value, apart from the database backups."
  value       = google_secret_manager_secret.main["SECUREONE_ENCRYPTION_KEY"].secret_id
}
