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
