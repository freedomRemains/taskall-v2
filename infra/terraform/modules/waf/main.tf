# CloudFrontにアタッチするWAFv2 WebACLは、CloudFrontの仕様上必ずus-east-1リージョンで
# 作成する必要があるため、呼び出し元(prod/main.tf)からus-east-1のprovider aliasを
# 明示的に受け取る(configuration_aliases)。

terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

# documents/design/2000007_aws_build_up.mdの方針通り、
# AWS Managed Rule(Core) + AWS Managed Rule(SQLi/XSS) + IPレート制限の3ルールのみを適用する
# 最小構成とする(費用最小方針、カスタムルールは追加しない)。
resource "aws_wafv2_web_acl" "cloudfront" {
  provider = aws.us_east_1

  name        = "${var.project_name}-cloudfront-waf"
  description = "Minimum WAF rules for CloudFront - Core, SQLi/XSS, IP rate limit"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # AWS Managed Rule(Core): XSSやサイズ制限違反等、汎用的な脅威パターンを防御する
  rule {
    name     = "aws-managed-common-rule-set"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  # AWS Managed Rule(SQLi/XSS): SQLインジェクション攻撃パターンを防御する
  rule {
    name     = "aws-managed-sqli-rule-set"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-sqli-rule-set"
      sampled_requests_enabled   = true
    }
  }

  # Log4Shell(CVE-2021-44228)等の既知の悪性入力パターンを防御する(checkov: CKV_AWS_192, CKV2_AWS_47)。
  # Core/SQLi同様、追加費用なしで適用できるAWS Managed Ruleのため、既存2ルールと併せて有効化する。
  rule {
    name     = "aws-managed-known-bad-inputs-rule-set"
    priority = 3

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-known-bad-inputs-rule-set"
      sampled_requests_enabled   = true
    }
  }

  # 同一IPからの短時間大量アクセス(DoS的な挙動)を制限する
  rule {
    name     = "ip-rate-limit"
    priority = 4

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.rate_limit
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-ip-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  # [許容リスク: CKV2_AWS_31] WAFログ(Kinesis Firehose経由でのS3/CloudWatch Logs出力)は追加費用が発生するため、
  # 監視スコープ外(documents/design/2000007_aws_build_up.md)に合わせて費用最小方針で見送る
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project_name}-cloudfront-waf"
    sampled_requests_enabled   = true
  }

  tags = {
    Name    = "${var.project_name}-cloudfront-waf"
    Project = var.project_name
  }
}
