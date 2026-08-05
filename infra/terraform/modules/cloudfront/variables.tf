variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "domain_name" {
  description = "CloudFrontにアタッチするカスタムドメイン名(例: taskall-v2.com)"
  type        = string
}

variable "origin_domain_name" {
  description = "オリジンのDNS名(CloudFrontの仕様上IPアドレス直接指定不可のため、EC2のElastic IPを指すDNS名を渡すこと)"
  type        = string
}

variable "origin_port" {
  description = "オリジン(EC2上のアプリ)へアクセスするポート番号"
  type        = number
}

variable "acm_certificate_arn" {
  description = "us-east-1で発行済みのACM証明書ARN"
  type        = string
}

variable "web_acl_arn" {
  description = "アタッチするWAFv2 WebACLのARN"
  type        = string
}
