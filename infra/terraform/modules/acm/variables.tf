variable "domain_name" {
  description = "証明書を発行するドメイン名(例: taskall-v2.com)"
  type        = string
}

variable "hosted_zone_id" {
  description = "DNS検証用レコードを作成するHosted Zone ID"
  type        = string
}

variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}
