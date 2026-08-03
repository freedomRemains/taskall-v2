# AWS環境構築

---

[READMEに戻る](../../README.md)

---

## 概要

- 本資料は [issue #27](https://github.com/freedomRemains/taskall-v2/issues/27) での討議結果をまとめたものです。
- 本プロジェクトにGitHub Actionsの設定を加え、AWS環境への自動リリースを行えるようにします。
- 本issueでは方針・仕様の検討のみを行い、具体的な実装作業（Terraformコード作成、GitHub Actions設定ファイル作成等）は
  「後続issueの分割案」節に挙げる複数の別issueに分けて実施します。
- インフラ初期構築の完了判定基準は、**セキュリティ面での考慮を行った上で構築したAWS環境上で、
  当該アプリのログイン操作（二段階認証）が確認できること**とします。

---

## 全体方針

- **費用最小構成を維持しつつ、AWS上のWebアプリとして最低限必要なセキュリティを初期段階から備える。**
- 監視（CloudWatchアラーム等）、SQLiteのバックアップ、RDS移行、ステージング環境分離は、
  いずれも初期構築のスコープ外とし、将来必要になったタイミングで別issueとして検討する
  （費用最小・スコープ集中の方針に合致するため）。
- Terraformの環境構成は、現時点ではprod環境のみを想定し、tfvarsやworkspaceによる環境分離は行わない
  （dev/stg環境が必要になった場合は、その時点で改めて検討する）。

---

## 初期構築の全体像（手動作業とIaC/スクリプトの役割分担）

- 本節は[issue #29](https://github.com/freedomRemains/taskall-v2/issues/29)での指摘を受けて追記した。
  「何を、どうすると、どうなるか」を初見の担当者でも追えるよう、AWS環境構築にあたって
  **初回のみ人手で行う必要がある事項**と、**Terraform／スクリプトが自動的に担う事項**を
  切り分けて示す。
- ここに挙げるのは、本資料（本issue #27での検討結果）の記載内容から判別できる範囲の事項のみ。
  各後続issue（「後続issueの分割案」節参照）で個別に判明した手動手順は、各issue対応時に
  本節へ追記していく運用とする。

### 全体の流れ

```
1. AWSアカウントの契約・ルートアカウントログイン           … 手動(初回のみ)
2. Terraform実行用の一時IAMクレデンシャル発行                … 手動(初回のみ、構築完了後に削除)
3. ローカル環境へのTerraform/AWS CLIインストール・認証設定  … 手動(初回のみ)
4. Terraform bootstrap構成の実行(state管理用S3+DynamoDB)    … 手動実行(terraform apply)
5. Route53でのドメイン新規取得                              … 手動(初回のみ)
6. Terraform prod構成の実行(VPC/EC2/SG/IAM Role等)          … 手動実行(terraform apply)
   └ 上記6で構築される個々のAWSリソース定義自体は           … IaC(Terraformコード)で管理
7. GitHub Actions CI/CD・EC2側デプロイスクリプトの動作       … 自動(CI/CD構築後は人手不要)
```

- 4・6は「人がコマンドを実行するタイミングは手動」だが、「構築されるリソースの内容」は
  Terraformコードにより再現可能な形でIaC管理されている。この違いを区別して読むこと。
- 1〜5は、Terraformコードや将来のGitHub Actions設定だけでは代替できない、**運用者による
  手作業が恒久的に残る事項**である。

### 初回のみ必要となる手動手順の詳細

- **AWSアカウントの契約・ルートアカウントログイン**
  - Terraformを含むあらゆるAWS操作の大前提であり、当然ながらコード化はできない。
- **Terraform実行用の一時IAMクレデンシャルの発行**
  - `documents/rules`等には未記載だが、本資料「publicリポジトリでのIaC資材管理の前提」節の
    通り、AWS環境構築時は一時的なクレデンシャルを発行し、構築完了後は直ちに削除する
    （再利用もしない）方針としている。
  - このクレデンシャルは、運用者がローカル環境で`terraform apply`を実行する際に
    `aws configure`等で設定するものであり、GitHub Actions用のOIDC連携（後述）とは別物である。
    OIDC連携はCI/CDが自動でAWSにアクセスするための仕組みだが、こちらは**人が手元でTerraformを
    実行するための一時的な認証情報**である。
  - IAMユーザ自体の作成・必要な権限（VPC/EC2/IAM/S3/DynamoDB等の操作権限）の付与は、
    AWSアカウント契約直後にはTerraformで管理するリソースが何も存在しないため、
    手動（AWSマネジメントコンソールまたはAWS CLI）で行う必要がある。
- **ローカル環境へのTerraform・AWS CLIインストール、認証情報設定**
  - `documents/procedure/3000021_terraform.md`の「前提ツール」節に従い、運用者の
    ローカル環境に一度だけセットアップする。
- **Terraform bootstrap構成の実行**
  - `infra/terraform/bootstrap`は、state管理用のS3バケット・DynamoDB Lockテーブル自体を
    構築するため、`prod`構成のようにS3バックエンドを使えない（stateを保管する場所自体を
    作る構成のため）。そのため`terraform apply`の実行は、他のTerraform構成同様に手動で
    行うが、bootstrap自体はプロジェクトを通じて最初の1回のみ実行すればよい。
  - 詳細な実行手順は`documents/procedure/3000021_terraform.md`を参照。
- **Route53でのドメイン新規取得**
  - 本資料「AWS構成」節の通り、ドメイン「www.taskall-v2.co.jp」はAWS Route53で新規取得する
    想定だが、Terraformで管理するのはRoute53 Hosted Zoneのみであり、ドメイン取得自体は
    AWSマネジメントコンソールから手動で行う（Terraformのaws_route53_zoneリソースは、
    取得済みドメインに対するHosted Zoneの管理のみを担う）。

---

## AWS構成

### 構成概要

```
利用者
  │ HTTPS
  ▼
Route53 (Hosted Zoneのみ管理。ドメイン「www.taskall-v2.co.jp」自体の取得は手動)
  │
  ▼
CloudFront + ACM証明書（無料・自動更新）
  │  ※ EC2への直接アクセスを遮断し、HTTPS終端とキャッシュによる負荷軽減を担う
  ▼
WAF（CloudFrontにアタッチ）
  │  - AWS Managed Rule（Core）
  │  - AWS Managed Rule（SQLi/XSS）
  │  - IPレート制限ルール
  ▼
EC2（t4g.small, Amazon Linux 2023 arm64, Public Subnet）
  │  - Security GroupはCloudFrontのIPレンジのみ許可（直アクセス禁止）
  │  - SSHポートは開放せず、SSM Session Manager経由でのみ運用操作を行う
  │  - OS更新はSSM Patch Managerで自動化する
  ▼
Webアプリ（Spring Boot + SQLite）
```

### EC2仕様

- インスタンスタイプは **t4g.small**（Graviton、ARM64）とする。費用最小の観点から、
  t4g.microより1段階上のt4g.smallを採用する（Spring BootのJVMプロセスがt4g.micro（1GiB）では
  メモリ不足になりやすいため）。
- OSは **Amazon Linux 2023 (arm64)** を使用する。Gravitonへの対応、無料利用、SSM Agent標準搭載
  という理由による。
- JVMヒープサイズは、t4g.small（メモリ2GiB）に収まるよう256〜512MB程度に抑える調整が必要になる。
  具体的なチューニングは実装issue側で対応する。

### DBについて

- AWS環境初期構築時点でもDBは **SQLite** のまま構築する（`documents/design/2000001_base_design.md`
  の方針通り、ローカル・本番ともにSQLiteを使用する現行方針を維持）。
- RDS移行は、同時接続数の増加・トランザクション負荷の増大・バックアップ要件の厳格化等の
  タイミングで改めて検討する（本issueのスコープ外）。

---

## CI/CDフロー

### 発動タイミング

- **feature→developのマージ時は、現行通りGitHub Actionsを実行せず、ローカル環境での確認のみとする**
  （現状の開発フローを変更しない）。
- **develop→mainのマージタイミングでのみ**GitHub Actionsを起動し、以下を自動実行する。
  1. Gradleビルド（全テスト含む）
  2. 成果物（アーティファクト）をAWS S3へアップロード
- リリース資材（S3上のアーティファクト）があるかどうかを判定してリリースを実行するのは、
  CI/CD側ではなくWebアプリサーバ（EC2）側とする（費用最小構成を実現するための役割分担）。

### GitHub ActionsからAWSへの認証方式

- **OIDC連携（GitHub Actions → AWS IAM Role）を採用し、長期のIAMユーザ・アクセスキーは発行しない。**
- GitHub Actions実行時にOIDCトークンを用いて一時的なAWS認証情報を取得し、S3へのアーティファクト
  アップロードを行う。長期クレデンシャルをGitHub Secretsに保存する運用は行わない
  （長期キーは漏洩時のリスクが継続するため、討議の結果OIDC方式へ変更した）。

### EC2側のデプロイ検知方式

- **EC2側でsystemdタイマーを用い、定期的にS3をポーリングする方式**とする（Lambda等によるプッシュ型
  通知は採用せず、EC2起点でのプル型とすることで構成をシンプルに保つ）。
- 新旧判定は、**S3オブジェクトのメタデータ（バージョンIDまたはタイムスタンプ）をEC2側で保持し、
  前回取得時との差分を検知する**方式とする。
- 差分を検知した場合、S3から最新のアーティファクトを取得し、アプリケーションを再起動する。

---

## Terraform構成

### ディレクトリ・環境構成

- `infra/terraform` 配下にAWS環境構築のスクリプトを配置する。
- 環境構成は現時点では **prod環境のみ**とし、tfvarsファイルは単一構成でよい
  （dev/stg環境が必要になった時点で、Terraform workspaceまたは環境別tfvarsの導入を改めて検討する）。

### Terraformで管理する対象リソース

- VPC / Subnet
- EC2（Security Group含む）
- IAM Role（GitHub Actions用OIDC Roleを含む）
- SSM関連設定
- CloudFront
- ACM証明書
- Route53（Hosted Zoneの管理。ドメイン取得自体は手動でAWS上から行う）
- WAF（ルール含む）

### state管理

- Terraformのstateは **S3 + DynamoDB Lock** を使用する。
- publicなGitHubリポジトリにstateファイルを絶対に含めない。

---

## セキュリティ・秘匿情報管理

### publicリポジトリでのIaC資材管理の前提

- AWS環境構築時は一時的なクレデンシャルを発行し、構築完了後は直ちに削除する（再利用もしない）。
- 秘匿情報はAWS Secrets ManagerまたはGitHub Secrets and variablesで管理する。
- 上記の前提のもと、publicなGitHubリポジトリにIaCコード（Terraform）を含める方針とする。

### 資材レビュー時の厳重確認事項

GitHubリポジトリにマージする資材に、次の情報が含まれていないか、必ずレビューする。

- access_key
- secret_key
- password
- api_key
- private_key
- jwt secret
- 証明書秘密鍵
- terraform.tfvars
- RDSのパスワード
- S3の署名付きURL
- 実際のCIDR（内部ネットワークが推測される場合）
- 実環境のホスト名や内部DNS名
- 実際のアカウントID（必要であれば `data "aws_caller_identity"` で取得し、ハードコードしない）

### 防護措置・予防措置

- `.gitignore` に `*.tfvars` を追加し、誤コミットを防止する。
- `terraform validate` をCIに組み込む。
- `tflint` や `checkov` を用いたIaCセキュリティチェックを実施する。
- GitHub ActionsのPRに対して自動でIaC lintを走らせる。

---

## スコープ外（将来課題として別issue化する項目）

- SQLite → RDSへの移行
- CloudWatch監視・アラームの追加
- SQLiteデータファイルのバックアップ（EBSスナップショット等）
- EC2インスタンスタイプの見直し（アクセス増加時等）
- ステージング環境等の環境分離（Terraform workspace／環境別tfvars導入）

---

## 後続issueの分割案

本issueでの検討結果をもとに、実際の資材作成は以下のような単位でissueを分割する想定とする。

- Terraform基盤（VPC / EC2 / Security Group / IAM Role）構築
- CloudFront / ACM / WAF / Route53構築
- GitHub Actions CI/CD構築（OIDC設定含む）
- EC2側デプロイスクリプト（systemdタイマーによるS3ポーリング・再起動）構築
