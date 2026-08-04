# ドメイン(taskall-v2.com)自体はAWSマネジメントコンソールから手動取得済みであり、
# Route53ドメイン取得時に自動作成されたHosted Zoneをdata sourceとして参照する。
# (Terraformで新規作成するとHosted Zoneが重複してしまうため、既存のものを参照する方針)

data "aws_route53_zone" "primary" {
  name         = "${var.domain_name}."
  private_zone = false
}

# CloudFrontのカスタムオリジンにはIPアドレスを直接指定できない(AWS API制約: InvalidArgument
# "The parameter origin name cannot be an IP address.")ため、EC2のElastic IPを指す専用の
# Aレコードを作成し、CloudFront側はこのDNS名をオリジンとして参照する。
# [許容リスク(誤検知): CKV2_AWS_23] Elastic IPは静的なIPアドレス値であり、Route53のalias機能で
# 参照できるAWSリソース(ELB/CloudFront等)のARNを持たないため、checkovが要求する
# 「AWSリソースへの直接アタッチ」を満たせない(EIP宛のAレコードでは構造上検知不可能な誤検知)。
resource "aws_route53_record" "origin" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "${var.origin_subdomain}.${var.domain_name}"
  type    = "A"
  ttl     = 300
  records = [var.ec2_public_ip]
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
