variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "noncurrent_version_expiration_days" {
  description = "旧バージョンのアーティファクトを保持する日数(それ以降は自動削除する)"
  type        = number
  default     = 30
}
