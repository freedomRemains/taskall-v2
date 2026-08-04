output "domain_name" {
  description = "CloudFrontディストリビューションのドメイン名(Route53 Aliasレコードのターゲット)"
  value       = aws_cloudfront_distribution.app.domain_name
}

output "hosted_zone_id" {
  description = "CloudFrontディストリビューションのHosted Zone ID(Route53 Aliasレコードで使用)"
  value       = aws_cloudfront_distribution.app.hosted_zone_id
}
