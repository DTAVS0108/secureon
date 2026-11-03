variable "project_id" {
  description = "Google Cloud project ID."
  type        = string
}

variable "region" {
  description = "Region, for example europe-west1."
  type        = string
}

variable "name" {
  description = "Prefix for every resource name."
  type        = string
  default     = "secureone"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.name))
    error_message = "Lower-case letters, digits and hyphens, 2 to 21 characters, starting with a letter."
  }
}

variable "public_base_url" {
  description = "The address people will use, for example https://id.example.com (map the domain to the service). Empty: the service's own run.app address."
  type        = string
  default     = ""
  validation {
    condition     = var.public_base_url == "" || can(regex("^https://[^/]+$", var.public_base_url))
    error_message = "An https:// address without a path or trailing slash, or empty."
  }
}

variable "image" {
  description = "SecureOne image (Cloud Run pulls public Docker Hub images directly)."
  type        = string
  default     = "docker.io/dmtavs0108/secureone:0.1.0"
}

variable "cpu" {
  description = "vCPUs for each instance."
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory for each instance; at least 2Gi."
  type        = string
  default     = "2Gi"
}

variable "max_instances" {
  description = "Most instances Cloud Run may start."
  type        = number
  default     = 3
}

variable "db_tier" {
  description = "Cloud SQL machine type."
  type        = string
  default     = "db-custom-1-3840"
}

variable "db_high_availability" {
  description = "Regional (high availability) Cloud SQL instance."
  type        = bool
  default     = false
}

variable "redis_memory_gb" {
  description = "Memorystore size in GB."
  type        = number
  default     = 1
}

variable "network_cidr" {
  description = "Subnet the Cloud Run instances use to reach the database and Redis (Direct VPC egress; at least a /26)."
  type        = string
  default     = "10.10.0.0/24"
}

variable "trusted_proxies" {
  description = "Addresses of Google's front end as the container sees them: they may tell SecureOne the visitor's own address. Check on the first deploy."
  type        = string
  default     = "169.254.0.0/16"
}

variable "deletion_protection" {
  description = "Protect the database and the service from deletion. Set to false before terraform destroy."
  type        = bool
  default     = true
}
