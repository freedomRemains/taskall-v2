# GitHub Actions CI/CD構築手順

---

[READMEに戻る](../../README.md)

---

## 概要

- 本資料は、[issue #34](https://github.com/freedomRemains/taskall-v2/issues/34)（
  [issue #27](https://github.com/freedomRemains/taskall-v2/issues/27)の後続issue）で構築した
  GitHub Actions CI/CDワークフローの内容と、GitHub側で必要な設定手順を示します。
- AWS側のTerraform資材（OIDC用IAM Role・アーティファクト用S3バケット）の構築手順は
  [documents/procedure/3000021_terraform.md](3000021_terraform.md)を参照してください。
- 設計方針（発動タイミング・OIDC連携・EC2側のポーリング方式等）の詳細は
  [documents/design/2000007_aws_build_up.md](../design/2000007_aws_build_up.md)の
  「CI/CDフロー」節を参照してください。

---

## ワークフロー構成

```
.github/workflows/
  cicd.yml            # develop→mainマージ時に起動するCI/CD本体
  terraform-lint.yml  # infra/terraform配下を変更するPull Requestに対するIaC静的チェック
```

### `cicd.yml`（develop→mainマージ時のCI/CD）

- トリガーは `main` ブランチへの `push`（develop→mainのマージ）のみとし、
  feature→developのマージ時は起動しない（現状の開発フローを変更しない方針）。
- `build-and-test`ジョブ: JDK 21をセットアップし、`./gradlew build`でビルド・全テストを実行する。
  失敗時調査用にテストレポート、後続ジョブ引き継ぎ用にビルド成果物(jar)をそれぞれ
  `actions/upload-artifact`でアップロードする。
- `upload-to-s3`ジョブ: `build-and-test`完了後、`aws-actions/configure-aws-credentials`で
  OIDC連携によりAWSの一時的な認証情報を取得し（`permissions: id-token: write`が必須）、
  ビルド成果物をAWS S3（アーティファクト用バケット）へアップロードする。
- S3にアップロードされたアーティファクトの検知・実際のリリース実行（アプリ再起動等）は
  EC2側の役割であり、本ワークフローのスコープ外（別issueでEC2側にsystemdタイマーを実装する）。

### `terraform-lint.yml`（IaC静的チェック）

- `infra/terraform/**`を変更するPull Requestに対して起動する。
- `terraform fmt -check` → `terraform validate`（bootstrap/prodそれぞれ個別に、
  `-backend=false`でstateバックエンドなしのinitを行う）→ `tflint` → `checkov` の順で
  静的チェックを行う。
- AWSへの実際のアクセスは行わない（`terraform plan`/`apply`は実行しない）ため、
  本ワークフロー自体にAWS認証情報の設定は不要。

---

## GitHub側で必要な手動設定

Terraformコード・ワークフローファイルだけでは完結せず、リポジトリ管理者が
GitHubのリポジトリ設定画面（Settings）から手動で行う必要がある設定です。

### 1. OIDC Identity Providerの信頼設定（AWS側、Terraform管理）

- `infra/terraform/modules/github_oidc_role`により、AWS側に
  `token.actions.githubusercontent.com`をIssuerとするOIDC Identity Providerと、
  対象リポジトリ・`main`ブランチからのみAssumeRoleを許可するIAM Roleを構築済み。
- `terraform apply`実行が前提のため、まだ実行していない場合は
  [documents/procedure/3000021_terraform.md](3000021_terraform.md)の手順に従うこと。

### 2. Repository Variablesの設定（GitHub側、手動）

`terraform apply`完了後の出力値を、対象リポジトリの
`Settings > Secrets and variables > Actions > Variables`タブから、以下のRepository Variable
として設定する（値はIAM Role ARN・S3バケット名であり、アクセスキーのような秘匿情報ではないため
Secretsではなく**Variables**として管理する）。

| Variable名                     | 設定値                                                        |
| ------------------------------- | -------------------------------------------------------------- |
| `AWS_GITHUB_ACTIONS_ROLE_ARN`    | `terraform apply`の`github_actions_role_arn`出力値              |
| `AWS_ARTIFACT_BUCKET_NAME`       | `terraform apply`の`artifact_bucket_name`出力値                 |
| `AWS_REGION`                     | （任意。未設定時は`cicd.yml`内のデフォルト値`ap-northeast-1`を使用） |

- これらの値はAWSアカウントの構築状況に依存し、環境によって変わりうるため、
  リポジトリのコードやドキュメントに実値をハードコードしない
  （`documents/design/2000007_aws_build_up.md`「資材レビュー時の厳重確認事項」節も参照）。

---

## 動作確認方法

- `develop`ブランチから`main`ブランチへPull Requestを作成しマージすると、`cicd.yml`が起動する。
- GitHub Actionsの実行ログで、`build-and-test`ジョブの全テスト成功、および
  `upload-to-s3`ジョブでのAWS認証情報取得・S3アップロードの成功を確認する。
- AWSマネジメントコンソールのS3から、アーティファクト用バケット（`taskallv2-artifact-<AWSアカウントID>`）
  配下の`releases/taskall-v2.jar`が更新されていることを確認する。
- `infra/terraform/**`を変更するPull Requestを作成すると、`terraform-lint.yml`が起動し、
  フォーマット・構文・セキュリティ設定チェックの結果がPRのチェックとして表示されることを確認する。

---

## 本issue（#34）のスコープ外

- EC2側でS3の新旧差分を検知し、実際にアプリケーションを再起動する処理
  （systemdタイマーによるS3ポーリング）は、後続issueで別途対応する
  （`documents/design/2000007_aws_build_up.md`「後続issueの分割案」節参照）。
