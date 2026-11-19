#!/bin/bash
# Runs once on first boot (EC2 user-data). Installs Docker, pulls the app source,
# and starts the container pointed at the RDS instance Terraform just created.
set -euxo pipefail

# A t3.micro has only 1 GB of memory. Building the app with Maven can run out of it,
# so add 2 GB of swap (disk space the server can use as extra, slower memory).
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
fi

dnf update -y
dnf install -y docker git
systemctl enable --now docker

git clone "${app_repo_url}" /opt/app
cd /opt/app

docker build -t benefits-portal-aws .

# The instance's public address, so links in emails point back to this server.
TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)

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
  -e NOTIFICATIONS_ENABLED="${email_enabled}" \
  -e NOTIFICATIONS_SENDER="ses" \
  -e NOTIFICATIONS_FROM="${email_from}" \
  -e NOTIFICATIONS_SES_REGION="${aws_region}" \
  -e NOTIFICATIONS_BASE_URL="http://$PUBLIC_IP:8080" \
  benefits-portal-aws
