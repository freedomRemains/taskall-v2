# 本番環境(prod)用のroot module。VPC/Security Group/IAM Role/EC2の各モジュールを結合する。
# 現時点ではprod環境のみを想定し、環境分離(workspace/環境別tfvars)は行わない。

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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

module "iam_ec2_role" {
  source = "../modules/iam_ec2_role"

  project_name = var.project_name
}

module "ec2" {
  source = "../modules/ec2"

  project_name          = var.project_name
  subnet_id             = module.vpc.public_subnet_id
  security_group_id     = module.security_group.security_group_id
  instance_profile_name = module.iam_ec2_role.instance_profile_name
  instance_type         = var.instance_type
}

# ACM証明書のDNS検証に、取得済みドメインの既存Hosted Zoneを参照する必要があるため、
# 先にHosted ZoneのみをRoute53モジュールから取得する(Aliasレコード自体はCloudFront構築後に作成)。
module "route53_zone" {
  source = "../modules/route53"

  domain_name               = var.domain_name
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

  project_name        = var.project_name
  domain_name         = var.domain_name
  origin_domain_name  = "www.taskall-v2.com"
  origin_port         = var.app_port
  acm_certificate_arn = module.acm.certificate_arn
  web_acl_arn         = module.waf.web_acl_arn
}
