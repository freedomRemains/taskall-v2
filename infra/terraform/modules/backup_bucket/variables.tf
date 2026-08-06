variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "expiration_days" {
  description = "DBバックアップを保持する日数(それ以降は自動削除する)"
  type        = number
  default     = 30
}
