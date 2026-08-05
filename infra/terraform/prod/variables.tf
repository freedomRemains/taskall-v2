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

variable "domain_name" {
  description = "Route53で取得済みのドメイン名(CloudFrontのカスタムドメイン・ACM証明書に使用)"
  type        = string
  default     = "taskall-v2.com"
}

variable "github_repository" {
  description = "GitHub Actions OIDC連携でAssumeRoleを許可する対象リポジトリ(\"<owner>/<repo>\"形式)"
  type        = string
  default     = "freedomRemains/taskall-v2"
}

variable "github_branch" {
  description = "GitHub Actions OIDC連携でAssumeRoleを許可する対象ブランチ(develop→mainマージ時のみCI/CDを起動するため、mainのみ許可する)"
  type        = string
  default     = "main"
}
