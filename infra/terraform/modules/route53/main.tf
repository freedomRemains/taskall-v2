# ドメイン(taskall-v2.com)自体はAWSマネジメントコンソールから手動取得済みであり、
# Route53ドメイン取得時に自動作成されたHosted Zoneをdata sourceとして参照する。
# (Terraformで新規作成するとHosted Zoneが重複してしまうため、既存のものを参照する方針)

data "aws_route53_zone" "primary" {
  name         = "${var.domain_name}."
  private_zone = false
}

# CloudFrontへのAliasレコード(Aレコード)。CloudFrontはグローバルなエッジロケーションを
# 持つためNS/TTLの概念を使わないAliasレコードで参照する(通常のCNAMEより高速かつ無料)。
resource "aws_route53_record" "apex_a" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

# IPv6経由のアクセスにも対応するため、AAAAレコードも併せて作成する(CloudFrontはIPv6を標準サポート)
resource "aws_route53_record" "apex_aaaa" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "AAAA"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
