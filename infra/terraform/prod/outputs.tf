output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "ec2_instance_id" {
  description = "EC2インスタンスID"
  value       = module.ec2.instance_id
}

output "ec2_public_ip" {
  description = "EC2に付与されたElastic IP(パブリックIP)"
  value       = module.ec2.public_ip
}
