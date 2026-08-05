variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "github_repository_id" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象リポジトリの不変ID(repository_idクレーム)。owner/repo名がリネームされてもズレないよう、信頼ポリシーの条件にはこちらを使用する"
  type        = string
}

variable "github_repository_owner_id" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象Organization/ユーザの不変ID(repository_owner_idクレーム)"
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

variable "oidc_thumbprint_url" {
  description = "GitHub ActionsのOIDCエンドポイントの証明書チェーンを取得するためのURL(サムプリント算出用)"
  type        = string
  default     = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}
