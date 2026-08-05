# GitHub Actions(develop→mainマージ時のCI/CD)からAWSへの認証はOIDC連携とし、
# 長期のIAMユーザ・アクセスキーは発行しない方針とする(issue #27での討議結果)。
# GitHub Actionsの実行時に発行される短命なOIDCトークンをAWS STSに提示し、
# 一時的な認証情報(AssumeRoleWithWebIdentity)を取得してS3へのアーティファクトアップロードを行う。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }

    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

# GitHubのOIDCエンドポイントの証明書チェーンを取得し、サムプリント(SHA1)を動的に算出する。
# GitHubのTLS証明書は発行元CAが将来変更されうるため、値をハードコードすると失効時に
# 更新が漏れるリスクがある。tls_certificateデータソースで都度取得することで、
# 証明書チェーンが変わってもterraform plan/apply時に自動追従できるようにする。
data "tls_certificate" "github_actions_oidc" {
  url = var.oidc_thumbprint_url
}

# GitHubのOIDCプロバイダをAWS IAMに登録する。
resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions_oidc.certificates[0].sha1_fingerprint]

  tags = {
    Name    = "${var.project_name}-github-actions-oidc"
    Project = var.project_name
  }
}

# GitHub ActionsがAssumeRoleWithWebIdentityでAssumeできるRole。
# GitHubはOrganization/リポジトリのリネームによるなりすまし対策として、リネーム履歴のある
# リポジトリではsubクレームを"repo:owner@<owner_id>/repo@<repo_id>:ref:..."という不変ID付き
# 形式に変更する場合があり、単純な"repo:owner/repo:ref:..."文字列比較では一致しなくなる
# (実際にAssumeRoleWithWebIdentityが"Not authorized"で失敗する不具合として発覚)。
# そのため、リネームの影響を受けないrepository_id/repository_owner_idクレーム(不変ID)を条件に
# 使用し、対象ブランチ(main)のみに限定する。他リポジトリ・他ブランチからのAssumeRoleを禁止する
# (feature/developブランチのCI実行では本Roleを使用しない設計のため)。
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
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:repository_id"
      values   = [var.github_repository_id]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:repository_owner_id"
      values   = [var.github_repository_owner_id]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:*:ref:refs/heads/${var.github_branch}"]
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
