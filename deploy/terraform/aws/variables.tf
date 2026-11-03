variable "name" {
  description = "Prefix for every resource name."
  type        = string
  default     = "secureone"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.name))
    error_message = "Lower-case letters, digits and hyphens, 2 to 21 characters, starting with a letter."
  }
}

variable "region" {
  description = "AWS region, for example eu-west-1."
  type        = string
}

variable "availability_zones" {
  description = "Two availability zones of the region, for example [\"eu-west-1a\", \"eu-west-1b\"]."
  type        = list(string)
  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "Give exactly two availability zones."
  }
}

variable "public_base_url" {
  description = "The address people will use, for example https://id.example.com. Point its DNS at the load balancer."
  type        = string
  validation {
    condition     = can(regex("^https://[^/]+$", var.public_base_url))
    error_message = "An https:// address without a path or trailing slash."
  }
}

variable "certificate_arn" {
  description = "ARN of an ACM certificate for the public_base_url host name, in the same region."
  type        = string
}

variable "image" {
  description = "SecureOne image."
  type        = string
  default     = "dmtavs0108/secureone:0.1.0"
}

variable "vpc_cidr" {
  description = "Address range of the new VPC (a /16). SecureOne trusts forwarded client addresses from it (the load balancer)."
  type        = string
  default     = "10.0.0.0/16"
  validation {
    condition     = can(regex("^\\d+\\.\\d+\\.0\\.0/16$", var.vpc_cidr))
    error_message = "A /16 range such as 10.0.0.0/16."
  }
}

variable "task_cpu" {
  description = "Fargate CPU units for each SecureOne task (1024 = 1 vCPU)."
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Fargate memory in MiB for each task; at least 2048."
  type        = number
  default     = 2048
  validation {
    condition     = var.task_memory >= 2048
    error_message = "SecureOne needs at least 2048 MiB."
  }
}

variable "desired_count" {
  description = "Number of SecureOne tasks."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.small"
}

variable "db_allocated_storage" {
  description = "RDS storage in GB."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Standby database in the second availability zone."
  type        = bool
  default     = false
}

variable "cache_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "deletion_protection" {
  description = "Protect the database and the load balancer from deletion. Set to false before terraform destroy."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Days to keep the container logs in CloudWatch."
  type        = number
  default     = 30
}
