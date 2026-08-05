# Terraform state管理用のS3バケット・DynamoDB Lockテーブルを構築するbootstrap構成。
# 本構成自体はlocal stateで管理する(状態管理用リソースを作る前段階のため、S3 backendは使えない)。
# prod環境等の他のTerraform構成は、ここで作成したS3バケット・DynamoDBテーブルをbackendとして使用する。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

# アカウントIDを取得し、S3バケット名の一意性を保証するために利用する
data "aws_caller_identity" "current" {}

# Terraform stateファイルを保管するS3バケット
# [許容リスク: CKV_AWS_18] state専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
# [許容リスク: CKV2_AWS_62] state専用バケットのためイベント通知は不要と判断
# [許容リスク: CKV_AWS_144] state専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
# [許容リスク: CKV_AWS_145] SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
resource "aws_s3_bucket" "terraform_state" {
  #checkov:skip=CKV_AWS_18:state専用の非公開バケットのため、アクセスログ用バケットは費用最小方針で見送る
  #checkov:skip=CKV2_AWS_62:state専用バケットのためイベント通知は不要と判断
  #checkov:skip=CKV_AWS_144:state専用バケットのため、クロスリージョンレプリケーションは費用最小方針で見送る
  #checkov:skip=CKV_AWS_145:SSE-S3(AES256)による暗号化で十分と判断し、KMS CMKは費用最小方針で見送る
  bucket = "${var.project_name}-terraform-state-${data.aws_caller_identity.current.account_id}"

  # 誤ってstateバケット自体を削除してしまう事故を防止する
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name    = "${var.project_name}-terraform-state"
    Project = var.project_name
  }
}

# バージョニングを有効化し、state破損時に過去バージョンへ復元できるようにする
resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# stateにはIAM Role等の機密情報同然の内容が含まれるため、サーバサイド暗号化を有効化する
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# パブリックアクセスを完全にブロックする(publicリポジトリでIaCコードを管理するため、なおさら厳重にする)
resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# バージョニングにより増え続ける旧バージョンのstateファイルを整理し、ストレージ費用を抑制する
# (checkov: CKV2_AWS_61)
resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    # 失敗したマルチパートアップロードの断片が残り続け、ストレージ費用が発生することを防ぐ(checkov: CKV_AWS_300)
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# Terraform実行の同時実行を防止するためのDynamoDB Lockテーブル
# [許容リスク: CKV_AWS_119] ロック情報のみを保持する低リスクテーブルのため、KMS CMKは費用最小方針で見送り、デフォルトのAWS所有キー暗号化を採用する
resource "aws_dynamodb_table" "terraform_lock" {
  #checkov:skip=CKV_AWS_119:ロック情報のみを保持する低リスクテーブルのため、KMS CMKは費用最小方針で見送り、デフォルトのAWS所有キー暗号化を採用する
  name         = "${var.project_name}-terraform-lock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  # ロック情報破損時の復元用にPITRを有効化する(PAY_PER_REQUESTかつ小規模テーブルのため追加費用は僅少)
  # (checkov: CKV_AWS_28)
  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name    = "${var.project_name}-terraform-lock"
    Project = var.project_name
  }
}
