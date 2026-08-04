output "zone_id" {
  description = "取得済みドメインのHosted Zone ID(ACM証明書のDNS検証で使用)"
  value       = data.aws_route53_zone.primary.zone_id
}
