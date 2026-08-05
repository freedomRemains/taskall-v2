# GitHub Actions CI/CDがビルド成果物(jar)をアップロードするS3バケット。
# EC2側はsystemdタイマーで本バケットを定期的にポーリングし、新旧差分検知後にデプロイする
# (EC2側のポーリング処理自体は本モジュールの対象外で、後続issueにて別途実装する)。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# アカウントIDを取得し、S3バケット名の一意性を保証するために利用する
data "aws_caller_identity" "current" {}

# [許容リスク: CKV_AWS_18] アーティファクト専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
# [許容リスク: CKV2_AWS_62] アーティファクト専用バケットのためイベント通知は不要と判断
# [許容リスク: CKV_AWS_144] アーティファクト専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
# [許容リスク: CKV_AWS_145] SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
resource "aws_s3_bucket" "artifact" {
  #checkov:skip=CKV_AWS_18:アーティファクト専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
  #checkov:skip=CKV2_AWS_62:アーティファクト専用バケットのためイベント通知は不要と判断
  #checkov:skip=CKV_AWS_144:アーティファクト専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
  #checkov:skip=CKV_AWS_145:SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
  bucket = "${var.project_name}-artifact-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name    = "${var.project_name}-artifact"
    Project = var.project_name
  }
}

# 新旧判定にバージョンIDを使えるようにするため、バージョニングを有効化する
resource "aws_s3_bucket_versioning" "artifact" {
  bucket = aws_s3_bucket.artifact.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artifact" {
  bucket = aws_s3_bucket.artifact.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# パブリックアクセスを完全にブロックする(publicリポジトリでIaCコードを管理するため、なおさら厳重にする)
resource "aws_s3_bucket_public_access_block" "artifact" {
  bucket = aws_s3_bucket.artifact.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# バージョニングにより増え続ける旧バージョンの成果物を整理し、ストレージ費用を抑制する
# (checkov: CKV2_AWS_61)
resource "aws_s3_bucket_lifecycle_configuration" "artifact" {
  bucket = aws_s3_bucket.artifact.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_expiration_days
    }

    # 失敗したマルチパートアップロードの断片が残り続け、ストレージ費用が発生することを防ぐ(checkov: CKV_AWS_300)
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
