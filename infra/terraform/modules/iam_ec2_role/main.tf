# EC2にSSM Session Manager経由でのみ運用操作を行うためのIAM Role。
# SSHポートは一切開放しないため、本Roleが唯一の運用アクセス経路となる。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${var.project_name}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = {
    Name    = "${var.project_name}-ec2-role"
    Project = var.project_name
  }
}

# SSM Session Manager接続・SSM Patch Managerによる自動更新のために必要な権限を付与する
resource "aws_iam_role_policy_attachment" "ssm_managed_instance_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# CloudWatch Agentがログ・メトリクスを送信するために必要な権限(issue #39)。
# AWS管理ポリシーのため、権限内容自体はAWS側の定義に追従する。
resource "aws_iam_role_policy_attachment" "cloudwatch_agent_server_policy" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# EC2側のリリーススクリプトがCI/CDアーティファクト(jar)をポーリング・取得するための最小権限(issue #39)。
# GitHub Actions用IAM Role(github_oidc_role)と同様、書き込みは許可せず読み取りのみに限定する。
data "aws_iam_policy_document" "ec2_permissions" {
  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [var.artifact_bucket_arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${var.artifact_bucket_arn}/*"]
  }

  # EC2側のバックアップスクリプト(毎日3時の定期バックアップ・リリース直前バックアップ)が
  # DBバックアップ用S3バケットへアップロードするための権限
  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [var.backup_bucket_arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${var.backup_bucket_arn}/*"]
  }

  # 特権管理者アカウントのメールアドレス・パスワードをSSM Parameter Store(SecureString)経由で
  # DBへ注入する方式に変更した(issue #39討議結果)。ただし実際の注入スクリプトの実装は別issueで
  # 対応するため、ここでは権限のみ先行して付与する。プレフィックスを本プロジェクト専用に限定し、
  # 他システムのパラメータへはアクセスできないようにする。
  statement {
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:*:*:parameter/${var.project_name}/*"]
  }
}

resource "aws_iam_role_policy" "ec2_permissions" {
  name   = "${var.project_name}-ec2-policy"
  role   = aws_iam_role.ec2.name
  policy = data.aws_iam_policy_document.ec2_permissions.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-instance-profile"
  role = aws_iam_role.ec2.name
}
