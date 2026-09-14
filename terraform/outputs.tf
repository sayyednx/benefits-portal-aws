output "app_public_ip" {
  description = "Visit http://<this>:8080 once the instance finishes booting (a minute or two after apply)."
  value       = aws_instance.app.public_ip
}

output "db_endpoint" {
  description = "RDS MySQL endpoint (not publicly reachable — the app instance talks to it over the VPC)."
  value       = aws_db_instance.mysql.address
}
