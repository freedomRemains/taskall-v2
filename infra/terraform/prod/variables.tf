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

# GitHubはOrganization/リポジトリのリネームに伴うなりすまし対策として、OIDCトークンのsubクレームを
# リネームの影響を受ける文字列ではなく不変ID付き形式に変更する場合があるため、信頼ポリシーの条件には
# 文字列(owner/repo)ではなく不変ID(repository_id/repository_owner_id)を使用する。値は
# `gh api repos/<owner>/<repo> --jq '.id,.owner.id'`、またはActions実行ログのOIDCトークンの
# repository_id/repository_owner_idクレームで確認できる。
variable "github_repository_id" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象リポジトリの不変ID(OIDCトークンのrepository_idクレーム)"
  type        = string
  default     = "1313485636"
}

variable "github_repository_owner_id" {
  description = "GitHub ActionsからのAssumeRoleを許可する対象Organization/ユーザの不変ID(OIDCトークンのrepository_owner_idクレーム)"
  type        = string
  default     = "188358132"
}

variable "github_branch" {
  description = "GitHub Actions OIDC連携でAssumeRoleを許可する対象ブランチ(develop→mainマージ時のみCI/CDを起動するため、mainのみ許可する)"
  type        = string
  default     = "main"
}
