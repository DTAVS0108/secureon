variable "subscription_id" {
  description = "Azure subscription ID."
  type        = string
}

variable "location" {
  description = "Azure region, for example westeurope."
  type        = string
}

variable "name" {
  description = "Prefix for every resource name (also the container app's name)."
  type        = string
  default     = "secureone"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.name))
    error_message = "Lower-case letters, digits and hyphens, 2 to 21 characters, starting with a letter."
  }
}

variable "public_base_url" {
  description = "The address people will use, for example https://id.example.com (add it to the container app as a custom domain). Empty: the app's own azurecontainerapps.io address."
  type        = string
  default     = ""
  validation {
    condition     = var.public_base_url == "" || can(regex("^https://[^/]+$", var.public_base_url))
    error_message = "An https:// address without a path or trailing slash, or empty."
  }
}

variable "image" {
  description = "SecureOne image."
  type        = string
  default     = "docker.io/dmtavs0108/secureone:0.1.0"
}

variable "vnet_cidr" {
  description = "Address range of the new virtual network (a /16). SecureOne trusts forwarded client addresses from it (the Container Apps ingress)."
  type        = string
  default     = "10.20.0.0/16"
  validation {
    condition     = can(regex("^\\d+\\.\\d+\\.0\\.0/16$", var.vnet_cidr))
    error_message = "A /16 range such as 10.20.0.0/16."
  }
}

variable "max_replicas" {
  description = "Most replicas of the container app."
  type        = number
  default     = 3
}

variable "db_sku" {
  description = "PostgreSQL flexible server SKU."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "db_storage_mb" {
  description = "PostgreSQL storage in MB."
  type        = number
  default     = 32768
}

variable "redis_sku" {
  description = "Azure Managed Redis SKU."
  type        = string
  default     = "Balanced_B0"
}

variable "log_retention_days" {
  description = "Days to keep the container logs."
  type        = number
  default     = 30
}
