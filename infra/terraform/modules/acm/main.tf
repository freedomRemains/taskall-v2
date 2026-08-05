# CloudFrontにアタッチするACM証明書は、CloudFrontの仕様上必ずus-east-1リージョンで
# 発行する必要があるため、呼び出し元(prod/main.tf)からus-east-1のprovider aliasを
# 明示的に受け取る(configuration_aliases)。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 5.0"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

# 検証方法はDNS検証を採用する(メール検証は運用者の手動対応が必要になり自動化できないため)
resource "aws_acm_certificate" "cert" {
  provider = aws.us_east_1

  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name    = "${var.project_name}-cert"
    Project = var.project_name
  }
}

# 証明書発行に必要なDNS検証用レコードを、取得済みドメインのHosted Zoneに自動作成する
resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cert.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }

  zone_id = var.hosted_zone_id
  name    = each.value.name
  type    = each.value.type
  records = [each.value.record]
  ttl     = 60
}

# DNS検証完了を待ち合わせ、検証済みの証明書ARNを後続のCloudFrontモジュールへ渡す
resource "aws_acm_certificate_validation" "cert" {
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.cert.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}
