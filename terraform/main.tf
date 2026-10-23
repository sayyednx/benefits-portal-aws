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
  identifier             = "${var.app_name}-db"
  engine                 = "mysql"
  engine_version         = "8.0"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.default.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  skip_final_snapshot    = true
  backup_retention_period = 0

  tags = { Name = "${var.app_name}-db" }
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

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    db_endpoint    = aws_db_instance.mysql.address
    db_name        = var.db_name
    db_username    = var.db_username
    db_password    = var.db_password
    admin_password = var.admin_password
    app_repo_url   = var.app_repo_url
  })

  tags = { Name = "${var.app_name}-app" }
}
