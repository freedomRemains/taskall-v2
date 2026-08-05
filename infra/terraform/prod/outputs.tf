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

output "artifact_bucket_name" {
  description = "CI/CDアーティファクト用S3バケット名(GitHub ActionsのRepository Variableに設定する)"
  value       = module.artifact_bucket.bucket_name
}

output "github_actions_role_arn" {
  description = "GitHub Actions CI/CDがOIDC連携でAssumeRoleするIAM Role ARN(GitHub ActionsのRepository Variableに設定する)"
  value       = module.github_oidc_role.role_arn
}
