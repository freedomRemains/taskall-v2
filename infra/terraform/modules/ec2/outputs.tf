output "instance_id" {
  description = "EC2インスタンスID"
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "EC2に付与されたElastic IP(パブリックIP)"
  value       = aws_eip.app.public_ip
}
