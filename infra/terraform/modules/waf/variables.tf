variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "rate_limit" {
  description = "IPレート制限のしきい値(5分間あたりの同一IPからのリクエスト数上限)"
  type        = number
  default     = 2000
}
