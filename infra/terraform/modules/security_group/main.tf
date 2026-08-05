# EC2への直アクセスを禁止し、CloudFront経由のアクセスのみを許可するSecurity Group。
# CloudFrontはまだ本issueでは構築しないが、AWS管理のプレフィックスリスト
# (com.amazonaws.global.cloudfront.origin-facing)は自プロジェクトのCloudFront構築有無に
# 関わらず利用可能なため、先行して参照できる。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# [許容リスク(誤検知): CKV2_AWS_5] modules/ec2でaws_instance.appにvpc_security_group_idsとしてアタッチしている(モジュール境界をまたぐためcheckovが検知できない誤検知)
resource "aws_security_group" "ec2" {
  #checkov:skip=CKV2_AWS_5:modules/ec2でaws_instance.appにvpc_security_group_idsとしてアタッチしている(モジュール境界をまたぐためcheckovが検知できない誤検知)
  name = "${var.project_name}-ec2-sg"
  # AWSのGroupDescriptionはASCII文字のみ対応のため、説明は英語で記述する
  description = "Allow app port access from CloudFront only; deny direct access and SSH"
  vpc_id      = var.vpc_id

  tags = {
    Name    = "${var.project_name}-ec2-sg"
    Project = var.project_name
  }
}

# CloudFrontのオリジンリクエストのみアプリポートへのアクセスを許可する(SSHポートは開放しない)
resource "aws_vpc_security_group_ingress_rule" "from_cloudfront" {
  security_group_id = aws_security_group.ec2.id
  # AWSのルールdescriptionもASCII文字のみ対応のため、説明は英語で記述する
  description = "Allow app port access from CloudFront"

  prefix_list_id = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol    = "tcp"
  from_port      = var.app_port
  to_port        = var.app_port
}

# SSM Agent通信・パッケージ取得等のためインターネットへの全outboundを許可する
resource "aws_vpc_security_group_egress_rule" "all_outbound" {
  security_group_id = aws_security_group.ec2.id
  # AWSのルールdescriptionもASCII文字のみ対応のため、説明は英語で記述する
  description = "Allow all outbound (for SSM communication, package retrieval, etc.)"

  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}
