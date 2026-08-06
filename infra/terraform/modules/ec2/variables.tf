variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "subnet_id" {
  description = "EC2を配置するSubnet ID"
  type        = string
}

variable "security_group_id" {
  description = "EC2にアタッチするSecurity Group ID"
  type        = string
}

variable "instance_profile_name" {
  description = "EC2にアタッチするIAM Instance Profile名"
  type        = string
}

variable "instance_type" {
  description = "EC2インスタンスタイプ"
  type        = string
  default     = "t4g.small"
}

variable "root_volume_size" {
  description = "ルートボリュームサイズ(GiB)"
  type        = number
  default     = 20
}

variable "aws_region" {
  description = "EC2側スクリプト(release.sh/backup_common.sh)がAWS CLIでAWSリソースへアクセスする際に使うリージョン"
  type        = string
}

variable "app_port" {
  description = "アプリケーションのポート番号(systemdサービスの起動引数・ヘルスチェックに使用)"
  type        = number
}

variable "artifact_bucket_name" {
  description = "EC2側のリリーススクリプトがポーリングするCI/CDアーティファクト用S3バケット名"
  type        = string
}

variable "backup_bucket_name" {
  description = "EC2側のバックアップスクリプトがアップロードするDBバックアップ用S3バケット名"
  type        = string
}

variable "log_group_retention_days" {
  description = "CloudWatch Logsのアプリケーションログ保持日数"
  type        = number
  default     = 365
}
