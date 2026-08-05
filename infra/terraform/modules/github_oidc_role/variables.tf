variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "github_repository" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象リポジトリ(\"<owner>/<repo>\"形式)"
  type        = string
}

variable "github_branch" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象ブランチ(develop→mainマージ時のみCI/CDを起動するため、mainのみ許可する)"
  type        = string
  default     = "main"
}

variable "artifact_bucket_arn" {
  description = "GitHub Actionsからのアップロードを許可するCI/CDアーティファクト用S3バケットのARN"
  type        = string
}
