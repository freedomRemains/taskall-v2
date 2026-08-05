variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "vpc_id" {
  description = "Security Groupを配置するVPCのID"
  type        = string
}

variable "app_port" {
  description = "CloudFrontからのアクセスを許可するアプリケーションのポート番号"
  type        = number
  default     = 8090
}
