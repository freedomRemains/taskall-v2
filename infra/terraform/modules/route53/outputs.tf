output "zone_id" {
  description = "取得済みドメインのHosted Zone ID(ACM証明書のDNS検証で使用)"
  value       = data.aws_route53_zone.primary.zone_id
}

output "origin_domain_name" {
  description = "CloudFrontのカスタムオリジンとして参照するDNS名(EC2のElastic IPを指すAレコード)"
  value       = aws_route53_record.origin.fqdn
}
