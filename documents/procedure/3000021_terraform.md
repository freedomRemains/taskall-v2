# Terraform 環境構築手順

---

[READMEに戻る](../../README.md)

---

## 概要

- 本資料は、`infra/terraform`配下のTerraform資材を使い、AWS環境（VPC / EC2 / Security Group /
  IAM Role / ACM / WAF / CloudFront / Route53 / GitHub Actions OIDC Role / CI/CDアーティファクト用S3 /
  DBバックアップ用S3）を構築する手順を示します。
- 資材構成の詳細な設計方針は
  [documents/design/2000007_aws_build_up.md](../design/2000007_aws_build_up.md)を参照してください。
- 本手順は[issue #29](https://github.com/freedomRemains/taskall-v2/issues/29)・
  [issue #32](https://github.com/freedomRemains/taskall-v2/issues/32)・
  [issue #34](https://github.com/freedomRemains/taskall-v2/issues/34)・
  [issue #39](https://github.com/freedomRemains/taskall-v2/issues/39)（いずれも
  [issue #27](https://github.com/freedomRemains/taskall-v2/issues/27)の後続issue）に対応します。
- GitHub Actions CI/CDワークフロー自体の説明・GitHub側の設定手順は
  [documents/procedure/3000031_github_actions_cicd.md](3000031_github_actions_cicd.md)を参照してください。
- EC2側の初期構築・リリース・バックアップスクリプト(`infra/ec2`配下)自体の説明は
  [documents/procedure/3000041_ec2_deploy_scripts.md](3000041_ec2_deploy_scripts.md)を参照してください。

---

## 前提ツール

- [Terraform](https://www.terraform.io/downloads.html)（1.5系以降）
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- AWSアカウントの認証情報（`aws configure`で設定済みであること）

---

## ディレクトリ構成

```
infra/terraform/
  bootstrap/   # Terraform state管理用のS3バケット・DynamoDB Lockテーブルを構築する(local state)
  prod/        # 本番環境(prod)のroot module。VPC/EC2/SG/IAM Role/ACM/WAF/CloudFront/Route53を
               # モジュール経由で構築する
  modules/
    vpc/            # VPC, Internet Gateway, Public Subnet, Route Table
    security_group/ # EC2用Security Group(CloudFront管理プレフィックスリストのみ許可)
    iam_ec2_role/   # EC2用IAM Role(SSM接続・CloudWatch Agent送信・S3ポーリング/バックアップ用) + Instance Profile
    ec2/            # EC2本体(t4g.small, Amazon Linux 2023 arm64) + Elastic IP + CloudWatch Log Group
                    # + 初期構築スクリプト(infra/ec2/init/init.sh.tftpl)をuser_dataとして注入
    acm/            # CloudFront用ACM証明書(DNS検証、us-east-1で発行)
    waf/            # CloudFrontにアタッチするWAFv2 WebACL(Core/SQLi等のAWS Managed Rule + IPレート制限)
    cloudfront/     # CloudFrontディストリビューション(EC2をカスタムオリジンとするHTTPS終端)
    route53/        # 取得済みドメインのHosted Zone参照 + CloudFrontへのAlias(A/AAAA)レコード
    artifact_bucket/  # GitHub Actions CI/CDがビルド成果物(jar)をアップロードするS3バケット
    backup_bucket/    # EC2側のバックアップスクリプトがDBバックアップをアップロードするS3バケット
    github_oidc_role/ # GitHub Actions用OIDCプロバイダ + AssumeRole用IAM Role(develop→mainマージ時のみ許可)
```

- 現時点ではprod環境のみを想定しており、環境分離（Terraform workspaceや環境別tfvars）は
  行っていません。
- ACM証明書・WAFv2(CLOUDFRONT scope)はAWSの仕様上us-east-1リージョンでのみ作成可能なため、
  `prod/main.tf`でus-east-1向けのprovider alias(`aws.us_east_1`)を追加し、`acm`/`waf`モジュール
  へ明示的に渡しています（EC2/VPC等はこれまで通り`var.region`(ap-northeast-1)で作成）。

---

## 1. bootstrap構成の実行（stateバックエンドの作成）

`prod`環境のTerraform stateをS3 + DynamoDB Lockで管理するため、最初に一度だけ実行します。
（既に構築済みの場合は本手順をスキップし、2に進んでください。）

```sh
cd infra/terraform/bootstrap
terraform init
terraform plan
terraform apply
```

- `apply`完了後に表示される`state_bucket_name`・`lock_table_name`の出力値を控えます。
- bootstrap構成自体はローカルstate（`terraform.tfstate`）で管理します。誤って削除しないよう
  注意してください（`.gitignore`によりGit管理対象外です）。

---

## 2. prod環境用backend設定の作成

```sh
cd infra/terraform/prod
cp backend.conf.example backend.conf
```

- `backend.conf`内の`bucket`（`taskallv2-terraform-state-<AWSアカウントID>`）を、
  bootstrap実行結果の`state_bucket_name`に合わせて書き換えます。
- `backend.conf`は環境固有の値を含むため`.gitignore`で除外されており、リポジトリには
  コミットしません（`backend.conf.example`のみコミット対象です）。

---

## 3. prod環境の構築

```sh
cd infra/terraform/prod
terraform init -backend-config=backend.conf
terraform plan
terraform apply
```

- `apply`完了後、`ec2_public_ip`の出力値でEC2に付与されたElastic IPを確認できます。
- SSH接続は行わず、AWSマネジメントコンソールまたはAWS CLIから
  `aws ssm start-session --target <instance_id>`でSSM Session Manager経由の接続を確認します。
- `apply`完了後、`cloudfront_domain_name`（CloudFrontディストリビューションのドメイン名）・
  `site_url`（`https://<取得済みドメイン>`）の出力値も確認できます。ACM証明書のDNS検証・
  CloudFrontディストリビューションの配信開始（Deployed状態への遷移）には数分〜数十分程度
  かかる場合があるため、`apply`完了直後は`site_url`へアクセスしてもエラーになることがあります。
- `apply`完了後、`artifact_bucket_name`（CI/CDアーティファクト用S3バケット名）・
  `github_actions_role_arn`（GitHub Actionsがaws-actions/configure-aws-credentialsで
  AssumeRoleする際に指定するIAM Role ARN）の出力値も確認できます。この2つの値は、
  GitHub Actionsワークフローが参照できるよう、GitHub側のRepository Variableとして
  手動で設定する必要があります（設定手順は
  [documents/procedure/3000031_github_actions_cicd.md](3000031_github_actions_cicd.md)参照）。
- `apply`完了後、`backup_bucket_name`（DBバックアップ用S3バケット名）の出力値も確認できます。
  EC2起動時のuser_data(`infra/ec2/init/init.sh.tftpl`)がこの値を自動的に設定値として
  埋め込むため、手動設定は不要です（詳細は
  [documents/procedure/3000041_ec2_deploy_scripts.md](3000041_ec2_deploy_scripts.md)参照）。

---

## 4. クリーンアップ（destroy）

構築した環境が不要になった場合は、逆順で削除します。

```sh
# 1. prod環境の削除
cd infra/terraform/prod
terraform destroy

# 2. bootstrap環境の削除(state管理用リソース自体を削除する場合のみ。通常は残したままでよい)
cd ../bootstrap
terraform destroy
```

- `bootstrap`のS3バケットには`prevent_destroy`を設定しているため、`destroy`前に
  Terraformコード側の当該設定を一時的に外すか、`terraform state rm`等で対象を除外する必要が
  あります（誤操作防止のための意図的な仕様です）。

---

## セキュリティチェックについて

- IaCコードのセキュリティチェックとして、`terraform fmt -recursive -check`・
  `terraform validate`・[checkov](https://www.checkov.io/)によるスキャンを実施しています。
- 費用最小方針との兼ね合いで意図的に許容しているリスク（例:
  DynamoDB/S3のKMS CMK不使用、VPC Flow Logs・EC2詳細モニタリング未設定等）は、
  各Terraformファイル内に理由をコメントで明記しています。
