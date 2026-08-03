variable "project_name" {
  description = "リソース命名に使うプロジェクト名の接頭辞"
  type        = string
}

variable "vpc_cidr" {
  description = "VPCのCIDRブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "Public SubnetのCIDRブロック"
  type        = string
  default     = "10.0.1.0/24"
}

variable "availability_zone" {
  description = "Public Subnetを配置するアベイラビリティゾーン"
  type        = string
  default     = "ap-northeast-1a"
}
