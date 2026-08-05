# GitHub Actions(develop→mainマージ時のCI/CD)からAWSへの認証はOIDC連携とし、
# 長期のIAMユーザ・アクセスキーは発行しない方針とする(issue #27での討議結果)。
# GitHub Actionsの実行時に発行される短命なOIDCトークンをAWS STSに提示し、
# 一時的な認証情報(AssumeRoleWithWebIdentity)を取得してS3へのアーティファクトアップロードを行う。

# GitHubのOIDCプロバイダをAWS IAMに登録する。
# thumbprint_listはGitHub ActionsのOIDCエンドポイント用として広く知られている値を設定するが、
# AWSは2023年以降、公開されている正規のCA証明書チェーンを自動的に信頼するため、
# 実際の検証はAWS管理のルート証明書ストアに基づいて行われる(thumbprintは形式上必須のため設定)。
resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea"]

  tags = {
    Name    = "${var.project_name}-github-actions-oidc"
    Project = var.project_name
  }
}

# GitHub ActionsがAssumeRoleWithWebIdentityでAssumeできるRole。
# subクレームを対象リポジトリ・対象ブランチ(main)のみに限定し、他リポジトリ・他ブランチからの
# AssumeRoleを禁止する(feature/developブランチのCI実行では本Roleを使用しない設計のため)。
data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/${var.github_branch}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${var.project_name}-github-actions-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json

  tags = {
    Name    = "${var.project_name}-github-actions-role"
    Project = var.project_name
  }
}

# CI/CDで必要な権限は、アーティファクト用S3バケットへのアップロード・一覧取得のみに限定する
# (最小権限の原則。EC2側の操作・他のAWSリソースへのアクセス権限は一切付与しない)。
data "aws_iam_policy_document" "github_actions_permissions" {
  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [var.artifact_bucket_arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${var.artifact_bucket_arn}/*"]
  }
}

resource "aws_iam_role_policy" "github_actions_permissions" {
  name   = "${var.project_name}-github-actions-policy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_permissions.json
}
