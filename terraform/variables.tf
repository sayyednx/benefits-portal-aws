variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "app_name" {
  description = "Short name used to tag/name resources."
  type        = string
  default     = "benefits-portal"
}

variable "instance_type" {
  description = "EC2 instance type running the app. t2.micro/t3.micro are free-tier eligible."
  type        = string
  default     = "t3.micro"
}

variable "db_instance_class" {
  description = "RDS instance class. db.t3.micro is free-tier eligible for 12 months."
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  type    = string
  default = "benefits_portal"
}

variable "db_username" {
  description = "Master username for the RDS instance."
  type        = string
  default     = "benefits_app"
}

variable "db_password" {
  description = "Master password for the RDS instance. Supply via TF_VAR_db_password or a .tfvars file — never commit it."
  type        = string
  sensitive   = true
}

variable "admin_password" {
  description = "Password for the portal's built-in admin account. Supply via TF_VAR_admin_password — never commit it."
  type        = string
  sensitive   = true
}

variable "app_repo_url" {
  description = "Git URL the EC2 instance clones to build and run the app."
  type        = string
  default     = "https://github.com/sayyednx/benefits-portal-aws.git"
}
