variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Used as a prefix for all resource names and tags"
  type        = string
  default     = "rate-limiter"
}

variable "environment" {
  description = "Deployment environment (prod / staging / dev)"
  type        = string
  default     = "prod"
}

variable "db_password" {
  description = "PostgreSQL master password — set in terraform.tfvars, never commit it"
  type        = string
  sensitive   = true
}

variable "app_image_tag" {
  description = "Docker image tag to deploy to ECS (set after `docker push`)"
  type        = string
  default     = "latest"
}
