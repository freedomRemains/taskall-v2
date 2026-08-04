# EC2への直アクセスを遮断しHTTPS終端を担うCloudFrontディストリビューション。
# オリジンはEC2のElastic IPへHTTP(app_port)で接続し、CloudFront-利用者間のみHTTPS化する
# (EC2側にTLS証明書を配置する必要がなく、証明書更新の運用コストがかからないため)。

# [許容リスク: CKV_AWS_86] アクセスログ用S3バケットは費用最小方針のため見送る(documents/design/2000007_aws_build_up.mdの監視スコープ外方針に合わせる)
# [許容リスク: CKV2_AWS_32] レスポンスヘッダーポリシー(セキュリティヘッダー付与)は初期構築のスコープ外とし、将来必要になった時点で別issueとして検討する
# [許容リスク: CKV_AWS_374] 対象は国内向けサービスに限定しないため、地理的制限(geo_restriction)は設けない方針とする
# [許容リスク: CKV_AWS_305] オリジンはS3静的サイトではなくEC2上の動的Webアプリのため、default_root_objectは適用対象外
# [許容リスク: CKV_AWS_310] オリジンはEC2単一構成(費用最小方針)のため、フェイルオーバー用の第2オリジンは設けない
# [許容リスク(誤検知): CKV2_AWS_47] modules/wafでAWSManagedRulesKnownBadInputsRuleSet(Log4j対策含む)を
# 既に有効化しているが、web_acl_idはmodule.waf側のARNをモジュール境界をまたいで参照するため、
# checkov側では当該WAFの中身まで解決できず誤検知となる(modules/security_groupのCKV2_AWS_5と同種の制約)。
resource "aws_cloudfront_distribution" "app" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.project_name} distribution"
  aliases         = [var.domain_name]
  # 費用最小方針のため、南米・オセアニア等を含まないPriceClass_200(北米/欧州/アジア中心)を採用する
  price_class = "PriceClass_200"
  web_acl_id  = var.web_acl_arn

  origin {
    domain_name = var.origin_domain_name
    origin_id   = "${var.project_name}-ec2-origin"

    # EC2側にTLS証明書を配置しない方針のため、オリジンへの接続はHTTPのみとする
    custom_origin_config {
      http_port              = var.origin_port
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "${var.project_name}-ec2-origin"
    viewer_protocol_policy = "redirect-to-https"

    # 動的なWebアプリ(セッション・CSRFトークン等)のため、Cookie/クエリ文字列を全て
    # オリジンへ転送し、CloudFront側ではキャッシュを行わない(min/default/max_ttl=0)
    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = {
    Name    = "${var.project_name}-cloudfront"
    Project = var.project_name
  }
}
