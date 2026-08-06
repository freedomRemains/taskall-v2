# EC2側の定期バックアップ(毎日3時のDBバックアップ・リリース直前バックアップ)を保存するS3バケット。
# artifact_bucket(CI/CDがjarをアップロードする用途)とは目的が異なり、保持期間の考え方も
# 異なる(バージョニングの旧バージョン数ではなく、経過日数で削除する)ため、専用バケットとして分離する。
# EC2ローカル(/opt/taskall-v2/history/)のみでは、EC2自体の障害(ディスク故障等)で
# 全データを失うリスクがあるため、S3側にも保存する(issue #39 討議結果)。

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

# [許容リスク: CKV_AWS_18] バックアップ専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
# [許容リスク: CKV2_AWS_62] バックアップ専用バケットのためイベント通知は不要と判断
# [許容リスク: CKV_AWS_144] バックアップ専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
# [許容リスク: CKV_AWS_145] SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
resource "aws_s3_bucket" "backup" {
  #checkov:skip=CKV_AWS_18:バックアップ専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
  #checkov:skip=CKV2_AWS_62:バックアップ専用バケットのためイベント通知は不要と判断
  #checkov:skip=CKV_AWS_144:バックアップ専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
  #checkov:skip=CKV_AWS_145:SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
  bucket = "${var.project_name}-backup-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name    = "${var.project_name}-backup"
    Project = var.project_name
  }
}

# 誤削除・誤上書きからの復旧余地を持たせるため、バージョニングは有効化する
# (旧バージョンの自動削除は、下記lifecycle設定のnoncurrent_version_expirationで行う)
resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# パブリックアクセスを完全にブロックする(publicリポジトリでIaCコードを管理するため、なおさら厳重にする)
resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 経過日数を基準に、現行バージョン・旧バージョンとも自動削除しストレージ費用を抑制する
# (checkov: CKV2_AWS_61)
resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "expire-old-backups"
    status = "Enabled"

    filter {}

    expiration {
      days = var.expiration_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.expiration_days
    }

    # 失敗したマルチパートアップロードの断片が残り続け、ストレージ費用が発生することを防ぐ(checkov: CKV_AWS_300)
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
