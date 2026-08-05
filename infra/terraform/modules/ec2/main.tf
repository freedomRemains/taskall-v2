# 費用最小構成に基づき、Gravitonインスタンス(t4g.small)・Amazon Linux 2023(arm64)を使用する。
# AMIはSSM Parameter Store経由で常に最新のものを自動取得する。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# [許容リスク: CKV_AWS_126] 詳細モニタリング(1分間隔)は追加費用が発生するため、監視のスコープ外(documents/design/2000007_aws_build_up.md)に合わせて見送る
resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.instance_profile_name

  # Nitroベースのt4g系はEBS最適化がデフォルトで有効だが、明示的に宣言する(checkov: CKV_AWS_135)
  ebs_optimized = true

  # IMDSv2を強制し、SSRF経由でのメタデータ窃取リスクを防止する(checkov: CKV_AWS_79)
  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  # SSHキーペアは使用しない(SSM Session Manager経由でのみ運用操作を行う方針のため)

  tags = {
    Name    = "${var.project_name}-app"
    Project = var.project_name
  }
}

# CloudFrontのオリジンとして安定したIPアドレスを使えるよう、Elastic IPを付与する。
# インスタンスが起動している間はElastic IPの利用に追加費用はかからない。
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name    = "${var.project_name}-app-eip"
    Project = var.project_name
  }
}
