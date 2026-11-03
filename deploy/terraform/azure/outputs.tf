output "app_url" {
  description = "The container app's own address."
  value       = "https://${azurerm_container_app.main.ingress[0].fqdn}"
}

output "setup_url" {
  description = "Open this to finish the setup; the setup code is in the app's log."
  value       = "${local.public_base_url}/admin/setup"
}

output "custom_domain_verification_id" {
  description = "For a custom domain: the TXT record value Azure asks for (asuid.<host>)."
  value       = azurerm_container_app_environment.main.custom_domain_verification_id
}
