variable "domain_name" {
  description = "Route53で取得済みのドメイン名(例: taskall-v2.com)"
  type        = string
}

variable "cloudfront_domain_name" {
  description = "AliasレコードのターゲットとなるCloudFrontディストリビューションのドメイン名"
  type        = string
}

variable "cloudfront_hosted_zone_id" {
  description = "AliasレコードのターゲットとなるCloudFrontディストリビューションのHosted Zone ID"
  type        = string
}
