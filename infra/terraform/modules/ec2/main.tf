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

# EC2側のデプロイスクリプト一式(init.sh・release.sh・backup_common.sh・systemdユニット等)は
# Terraform管理外の`infra/ec2`配下に配置している(issue #39)。理由は次の通り。
#   - シェルスクリプト自体はTerraformの記法に依存しない汎用的な資材であり、単体でも
#     読める・shellcheck等で検証できる状態を保ちたいため。
#   - Terraform側は、これらのファイルをuser_data(EC2起動時に1回だけ実行されるcloud-init
#     スクリプト)へ埋め込む「配線」の役割に徹する。
#
# これらのファイル本体をuser_dataへ直接埋め込むと、AWSの仕様上の上限(16KB)を超過して
# terraform planが失敗する(issue #44)。そのため、ファイル本体はTerraform側でS3
# (既存のartifact_bucket、release.sh用に既にEC2ロールへread権限付与済み)へ事前配置し、
# user_data(init.sh.tftpl)側はS3から取得するだけの最小限のブートストラップに留める。
locals {
  ec2_scripts_dir    = "${path.module}/../../../ec2"
  ec2_scripts_prefix = "ec2-scripts"

  # S3オブジェクトキー(ファイル名) -> ローカルファイルパスの対応表。
  # init.sh.tftpl側は同じファイル名でS3から取得するため、キー名は配置先ファイル名と一致させる。
  ec2_script_files = {
    "taskall-v2.service"           = "${local.ec2_scripts_dir}/init/files/taskall-v2.service"
    "taskall-v2-release.service"   = "${local.ec2_scripts_dir}/init/files/taskall-v2-release.service"
    "taskall-v2-release.timer"     = "${local.ec2_scripts_dir}/init/files/taskall-v2-release.timer"
    "taskall-v2-backup.service"    = "${local.ec2_scripts_dir}/init/files/taskall-v2-backup.service"
    "taskall-v2-backup.timer"      = "${local.ec2_scripts_dir}/init/files/taskall-v2-backup.timer"
    "logrotate.conf"               = "${local.ec2_scripts_dir}/init/files/logrotate.conf"
    "cloudwatch-agent-config.json" = "${local.ec2_scripts_dir}/init/files/cloudwatch-agent-config.json"
    "release.sh"                   = "${local.ec2_scripts_dir}/release/release.sh"
    "backup_common.sh"             = "${local.ec2_scripts_dir}/release/backup_common.sh"
    "render-secrets-env.sh"        = "${local.ec2_scripts_dir}/init/files/render-secrets-env.sh"
    "update-ec2-scripts.sh"        = "${local.ec2_scripts_dir}/init/files/update-ec2-scripts.sh"
  }
}

# EC2側デプロイスクリプト一式を、user_data経由ではなくS3経由でEC2へ配置するためのアップロード(issue #44)。
# etagにfilemd5を使うことで、ファイル内容が変わった場合のみオブジェクトが更新される。
resource "aws_s3_object" "ec2_scripts" {
  for_each = local.ec2_script_files

  bucket = var.artifact_bucket_name
  key    = "${local.ec2_scripts_prefix}/${each.key}"
  source = each.value
  etag   = filemd5(each.value)

  tags = {
    Name    = "${var.project_name}-ec2-script-${each.key}"
    Project = var.project_name
  }
}

# CloudWatch Agentが送信するアプリケーションログの格納先。タグ・保持期間をTerraform側で
# 明示的に管理し、CloudWatch Agent起動前にLog Groupが存在する状態にしておく(issue #39)。
# [許容リスク: CKV_AWS_158] CloudWatch Logsのデフォルト暗号化(AWS管理キー)で十分と判断し、
# 他バケット同様、費用最小方針によりKMS CMKは見送る
resource "aws_cloudwatch_log_group" "app" {
  #checkov:skip=CKV_AWS_158:CloudWatch Logsのデフォルト暗号化(AWS管理キー)で十分と判断し、費用最小方針によりKMS CMKは見送る
  name              = "/taskall-v2/application"
  retention_in_days = var.log_group_retention_days

  tags = {
    Name    = "taskall-v2-log"
    Project = var.project_name
  }
}

# [許容リスク: CKV_AWS_126] 詳細モニタリング(1分間隔)は追加費用が発生するため、監視のスコープ外(documents/design/2000007_aws_build_up.md)に合わせて見送る
resource "aws_instance" "app" {
  #checkov:skip=CKV_AWS_126:詳細モニタリング(1分間隔)は追加費用が発生するため、監視のスコープ外に合わせて見送る
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.instance_profile_name

  # EC2起動時(cloud-init)に一度だけ実行され、CloudWatch Agent・systemdサービス/タイマー・
  # logrotate等の初期構築を行う(issue #39、infra/ec2/init/init.sh.tftpl参照)。
  # ファイル本体はaws_s3_object.ec2_scripts側で事前にS3へ配置済みのため、ここではバケット名・
  # プレフィックスのみ渡し、init.sh側でS3から取得させる(issue #44、user_dataの16KB上限対応)。
  user_data = templatefile("${local.ec2_scripts_dir}/init/init.sh.tftpl", {
    project_name         = var.project_name
    aws_region           = var.aws_region
    artifact_bucket_name = var.artifact_bucket_name
    backup_bucket_name   = var.backup_bucket_name
    app_port             = var.app_port
    ec2_scripts_prefix   = local.ec2_scripts_prefix
  })

  # user_data実行(cloud-init)時点でS3上にスクリプト本体が存在している必要があるため、
  # 先にaws_s3_object.ec2_scriptsのアップロードが完了していることを保証する
  depends_on = [aws_s3_object.ec2_scripts]

  # user_data(init.sh.tftpl)の内容が変わった場合、terraform apply時にEC2インスタンスを
  # 再作成(force replacement)させ、初期構築スクリプトの変更をplan時点で明示的に気付けるように
  # する(issue #48)。既定値はfalseのため、この設定が無いと単にterraform apply済みの
  # 既存インスタンスに対してuser_dataを差し替えても、次回起動(再起動)まで実質適用されず、
  # 変更したはずの初期構築処理が実行されない問題が起き得る。
  user_data_replace_on_change = true

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
