output "security_group_id" {
  description = "EC2用Security GroupのID"
  value       = aws_security_group.ec2.id
}
