# Terraform 環境構築手順

---

[READMEに戻る](../../README.md)

---

## 概要

- 本資料は、`infra/terraform`配下のTerraform資材を使い、AWS環境（VPC / EC2 / Security Group /
  IAM Role）を構築する手順を示します。
- 資材構成の詳細な設計方針は
  [documents/design/2000007_aws_build_up.md](../design/2000007_aws_build_up.md)を参照してください。
- 本手順は[issue #29](https://github.com/freedomRemains/taskall-v2/issues/29)（
  [issue #27](https://github.com/freedomRemains/taskall-v2/issues/27)の後続issue）に対応します。

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
  prod/        # 本番環境(prod)のroot module。VPC/EC2/SG/IAM Roleをモジュール経由で構築する
  modules/
    vpc/            # VPC, Internet Gateway, Public Subnet, Route Table
    security_group/ # EC2用Security Group(CloudFront管理プレフィックスリストのみ許可)
    iam_ec2_role/   # EC2用IAM Role(SSM接続用) + Instance Profile
    ec2/            # EC2本体(t4g.small, Amazon Linux 2023 arm64) + Elastic IP
```

- 現時点ではprod環境のみを想定しており、環境分離（Terraform workspaceや環境別tfvars）は
  行っていません。

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
