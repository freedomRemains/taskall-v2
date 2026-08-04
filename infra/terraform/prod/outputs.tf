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

output "cloudfront_domain_name" {
  description = "CloudFrontディストリビューションのドメイン名"
  value       = module.cloudfront.domain_name
}

output "site_url" {
  description = "利用者がアクセスするURL(カスタムドメイン経由)"
  value       = "https://${var.domain_name}"
}
