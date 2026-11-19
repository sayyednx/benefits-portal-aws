terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# --- Networking: use the account's default VPC rather than standing up a new one.
# Keeps this approachable for a first AWS project; a production setup would use a
# dedicated VPC with private subnets for the database.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# --- Security groups ---

resource "aws_security_group" "app" {
  name        = "${var.app_name}-app-sg"
  description = "Allows HTTP to the app only. No SSH port is open."
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "App port"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.app_name}-app-sg" }
}

resource "aws_security_group" "db" {
  name        = "${var.app_name}-db-sg"
  description = "Allows MySQL only from the app security group, never from the internet."
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "MySQL from app instances only"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.app_name}-db-sg" }
}

# --- Database ---

resource "aws_db_subnet_group" "default" {
  name       = "${var.app_name}-db-subnets"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "mysql" {
  identifier              = "${var.app_name}-db"
  engine                  = "mysql"
  engine_version          = "8.0"
  instance_class          = var.db_instance_class
  allocated_storage       = 20
  db_name                 = var.db_name
  username                = var.db_username
  password                = var.db_password
  db_subnet_group_name    = aws_db_subnet_group.default.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  publicly_accessible     = false
  skip_final_snapshot     = true
  backup_retention_period = 0

  tags = { Name = "${var.app_name}-db" }
}

# --- Document storage (S3) ---
# Claimants upload proof like pay stubs and layoff letters. These hold personal
# information, so the bucket is locked down: no public access at all, encrypted at
# rest, and versioned so an overwritten or deleted file can be recovered.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "documents" {
  # Bucket names are global across all of AWS, so the account id keeps this one unique.
  bucket = "${var.app_name}-documents-${data.aws_caller_identity.current.account_id}"

  # Lets `terraform destroy` remove the bucket even with files in it. Fine for a demo;
  # a real benefits program would keep these records for years instead.
  force_destroy = true

  tags = { Name = "${var.app_name}-documents" }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

# --- IAM: what the app server is allowed to do in AWS ---
# The EC2 instance gets a role instead of access keys. AWS hands the app short-lived
# credentials automatically, so there are no long-lived secrets to leak. The policy
# allows only reading and writing files in this one bucket ("least privilege").

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.app_name}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

data "aws_iam_policy_document" "documents_access" {
  statement {
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${aws_s3_bucket.documents.arn}/*"]
  }
}

resource "aws_iam_role_policy" "documents_access" {
  name   = "documents-read-write"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.documents_access.json
}

# --- Email (Amazon SES) ---
# SES sends the claimant emails. AWS only lets you send from an address you have proved
# you own, so Terraform registers var.notification_from_email and AWS emails it a link to
# click. Until you click it, emails can't be sent (the app keeps working, it just logs it).
# New SES accounts are also in a "sandbox": they can only send TO verified addresses too,
# which is fine for a demo where the claimant email is your own.

resource "aws_sesv2_email_identity" "sender" {
  count          = var.notification_from_email == "" ? 0 : 1
  email_identity = var.notification_from_email
}

# The app may send email only from that one address, and nothing else in SES.
data "aws_iam_policy_document" "send_email" {
  count = var.notification_from_email == "" ? 0 : 1
  statement {
    actions   = ["ses:SendEmail"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.notification_from_email]
    }
  }
}

resource "aws_iam_role_policy" "send_email" {
  count  = var.notification_from_email == "" ? 0 : 1
  name   = "send-claimant-email"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.send_email[0].json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.app_name}-app-profile"
  role = aws_iam_role.app.name
}

# --- App server ---
# Ships the RDS endpoint and app secrets to the instance via user_data, then has it
# clone this repo and run `docker compose up` for just the app service (the database
# it talks to is RDS, not the mysql container from docker-compose.yml).

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  # IMDSv2 only (the safer version of the instance metadata service). The hop limit of 2
  # lets the app inside its Docker container still reach it to get the role's credentials.
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    db_endpoint    = aws_db_instance.mysql.address
    db_name        = var.db_name
    db_username    = var.db_username
    db_password    = var.db_password
    admin_password = var.admin_password
    app_repo_url   = var.app_repo_url
    aws_region     = var.aws_region
    docs_bucket    = aws_s3_bucket.documents.bucket
    email_enabled  = var.notification_from_email == "" ? "false" : "true"
    email_from     = var.notification_from_email
  })

  tags = { Name = "${var.app_name}-app" }
}
