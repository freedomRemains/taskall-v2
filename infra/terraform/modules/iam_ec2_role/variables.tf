variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "artifact_bucket_arn" {
  description = "EC2側のリリーススクリプトがポーリング・取得するCI/CDアーティファクト用S3バケットARN"
  type        = string
}

variable "backup_bucket_arn" {
  description = "EC2側のバックアップスクリプトがアップロードするDBバックアップ用S3バケットARN"
  type        = string
}
