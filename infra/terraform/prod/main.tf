# 本番環境(prod)用のroot module。VPC/Security Group/IAM Role/EC2の各モジュールを結合する。
# 現時点ではprod環境のみを想定し、環境分離(workspace/環境別tfvars)は行わない。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }

    # modules/github_oidc_role でGitHubのOIDCエンドポイント証明書サムプリントを動的取得するために使用
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # backendの実値(バケット名等)はbackend.conf(bootstrap実行後に作成)を
  # -backend-config オプションで渡す。ここでは接続先種別のみ宣言する(部分設定)。
  backend "s3" {}
}

provider "aws" {
  region = var.region
}

# ACM証明書(CloudFront用)・WAFv2(CLOUDFRONT scope)はAWSの仕様上us-east-1リージョンでのみ
# 作成可能なため、専用のprovider aliasを用意する(EC2/VPC等は引き続き var.region で作成する)。
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

module "vpc" {
  source = "../modules/vpc"

  project_name = var.project_name
}

module "security_group" {
  source = "../modules/security_group"

  project_name = var.project_name
  vpc_id       = module.vpc.vpc_id
  app_port     = var.app_port
}

# GitHub Actions CI/CDがビルド成果物(jar)をアップロードするS3バケット。
# EC2側のIAM Role(release.shのポーリング対象)より前段で参照する必要があるため、
# ここでモジュール呼び出し順を先に配置する。
module "artifact_bucket" {
  source = "../modules/artifact_bucket"

  project_name = var.project_name
}

# EC2側のバックアップスクリプトがDBバックアップをアップロードするS3バケット(issue #39)
module "backup_bucket" {
  source = "../modules/backup_bucket"

  project_name = var.project_name
}

module "iam_ec2_role" {
  source = "../modules/iam_ec2_role"

  project_name        = var.project_name
  artifact_bucket_arn = module.artifact_bucket.bucket_arn
  backup_bucket_arn   = module.backup_bucket.bucket_arn
}

module "ec2" {
  source = "../modules/ec2"

  project_name          = var.project_name
  subnet_id             = module.vpc.public_subnet_id
  security_group_id     = module.security_group.security_group_id
  instance_profile_name = module.iam_ec2_role.instance_profile_name
  instance_type         = var.instance_type
  aws_region            = var.region
  app_port              = var.app_port
  artifact_bucket_name  = module.artifact_bucket.bucket_name
  backup_bucket_name    = module.backup_bucket.bucket_name
}

# ACM証明書のDNS検証・CloudFrontオリジン用Aレコードに、取得済みドメインの既存Hosted Zoneを
# 参照する必要があるため、先にRoute53モジュールを呼び出す(ドメイン頂点へのAliasレコードのみ
# CloudFront構築後の値に依存する)。
module "route53_zone" {
  source = "../modules/route53"

  domain_name               = var.domain_name
  ec2_public_ip             = module.ec2.public_ip
  cloudfront_domain_name    = module.cloudfront.domain_name
  cloudfront_hosted_zone_id = module.cloudfront.hosted_zone_id
}

module "acm" {
  source = "../modules/acm"
  providers = {
    aws.us_east_1 = aws.us_east_1
  }

  project_name   = var.project_name
  domain_name    = var.domain_name
  hosted_zone_id = module.route53_zone.zone_id
}

module "waf" {
  source = "../modules/waf"
  providers = {
    aws.us_east_1 = aws.us_east_1
  }

  project_name = var.project_name
}

module "cloudfront" {
  source = "../modules/cloudfront"

  project_name = var.project_name
  domain_name  = var.domain_name
  # CloudFrontのカスタムオリジンにIPアドレスを直接指定するとAWS API側でエラーになるため、
  # module.route53_zoneで作成したDNS名(origin.<domain_name>)経由でEC2のElastic IPを参照する
  origin_domain_name  = module.route53_zone.origin_domain_name
  origin_port         = var.app_port
  acm_certificate_arn = module.acm.certificate_arn
  web_acl_arn         = module.waf.web_acl_arn
}

# GitHub Actions CI/CDがOIDC連携でAssumeRoleするIAM Role(develop→mainマージ時のみ使用)
module "github_oidc_role" {
  source = "../modules/github_oidc_role"

  project_name               = var.project_name
  github_repository_id       = var.github_repository_id
  github_repository_owner_id = var.github_repository_owner_id
  github_branch              = var.github_branch
  artifact_bucket_arn        = module.artifact_bucket.bucket_arn
}
