variable "region" {
  description = "AWSリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
  default     = "taskallv2"
}

variable "app_port" {
  description = "CloudFrontからのアクセスを許可するアプリケーションのポート番号"
  type        = number
  default     = 8090
}

variable "instance_type" {
  description = "EC2インスタンスタイプ"
  type        = string
  default     = "t4g.small"
}
