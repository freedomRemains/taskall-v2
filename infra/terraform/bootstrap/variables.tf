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
