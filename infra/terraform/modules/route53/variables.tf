variable "domain_name" {
  description = "Route53で取得済みのドメイン名(例: taskall-v2.com)"
  type        = string
}

variable "ec2_public_ip" {
  description = "オリジン用Aレコードのターゲットとなる、EC2に付与されたElastic IP"
  type        = string
}

variable "origin_subdomain" {
  description = "CloudFrontのオリジン用に作成するサブドメインの接頭辞(例: origin → origin.taskall-v2.com)"
  type        = string
  default     = "origin"
}

variable "cloudfront_domain_name" {
  description = "AliasレコードのターゲットとなるCloudFrontディストリビューションのドメイン名"
  type        = string
}

variable "cloudfront_hosted_zone_id" {
  description = "AliasレコードのターゲットとなるCloudFrontディストリビューションのHosted Zone ID"
  type        = string
}
