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
