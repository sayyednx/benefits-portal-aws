#!/bin/bash
# Runs once on first boot (EC2 user-data). Installs Docker, pulls the app source,
# and starts the container pointed at the RDS instance Terraform just created.
set -euxo pipefail

dnf update -y
dnf install -y docker git
systemctl enable --now docker

git clone "${app_repo_url}" /opt/app
cd /opt/app

docker build -t benefits-portal-aws .

docker run -d \
  --name benefits-portal-aws \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://${db_endpoint}/${db_name}" \
  -e SPRING_DATASOURCE_USERNAME="${db_username}" \
  -e SPRING_DATASOURCE_PASSWORD="${db_password}" \
  -e ADMIN_USERNAME="admin" \
  -e ADMIN_PASSWORD="${admin_password}" \
  -e DOCUMENTS_STORAGE="s3" \
  -e DOCUMENTS_S3_BUCKET="${docs_bucket}" \
  -e DOCUMENTS_S3_REGION="${aws_region}" \
  benefits-portal-aws
