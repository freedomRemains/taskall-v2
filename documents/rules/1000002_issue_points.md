# 各issueのポイントまとめ

---

[READMEに戻る](../../README.md)

---

## 概要

本資料では、各issueで対応・議論した内容のうち、今後の開発でも参照価値の高いポイントを、
issue単位で簡潔にまとめます。issueやpull requestの全文を毎回読み返さなくても、
本資料を見れば経緯・結論・関連ファイルが把握できることを目的とします。

- 新しいissueの対応が完了した際は、本資料の末尾に`###`見出しでセクションを追加してください。
- 記述は箇条書きを基本とし、issue URL・PR URL・関連する相対パスを明記してください。
- 本資料はissue #21の対応から運用を開始しています。それ以前のissue（#13, #15, #17, #19等）に
  ついては、必要に応じて別途追記します。

---

### issue #21: messages.propertiesのログレベル／例外種別の規約整理

- issue: https://github.com/freedomRemains/taskall-v2/issues/21
- PR: https://github.com/freedomRemains/taskall-v2/pull/22
- `messages.properties`のメッセージキーは、`msg.err.*`と`msg.warn.*`の2種類の接頭辞を使い分ける。
  - `msg.err.*`: `ApplicationInternalException`をスローし`logger.error`でログ出力する
    （システムが継続動作できない、システム的なエラー）。
  - `msg.warn.*`: `BusinessRuleViolationException`をスローする、または`logger.warn`で
    直接ログ出力する（業務的・入力値的なエラーで、システムは継続動作する）。
- Spring Securityの`AuthenticationProvider`実装（`BadCredentialsException`,
  `LockedException`, 独自の`TwoFactorRequiredException`等）は、Spring Security側の
  認証処理契約上`AuthenticationException`派生でなければならないため、上記規約の例外として
  許容する（`VerifyTwoFactorAuthService.java`等）。
- 本issueで実施した具体的な修正:
  - `msg.err.web.businessRuleViolation` → `msg.warn.web.businessRuleViolation`に改名
    （`GlobalExceptionHandler.java`のログレベルも`logger.error`→`logger.warn`に変更）。
  - `msg.err.common.db.columnDefNotFound`（`CreateTableSqlBuilder.java`,
    `SelectSqlBuilder.java`）、`msg.err.common.db.columnDefForColumnNotFound`
    （`InsertSqlBuilder.java`）、`msg.err.common.db.fileNotFound`／
    `msg.err.common.db.headerRowNotFound`（`TsvTableFileReader.java`）は、
    キー名はそのまま（`msg.err`）とし、スローする例外を`BusinessRuleViolationException`
    から`ApplicationInternalException`に変更（TBL_DEF等の資材データ不整合はシステム
    運用継続不可能なエラーと判断したため）。
  - `msg.err.web.roleRestriction`（`GetAccountService.java`）、
    `msg.err.web.requiredParamMissing`（`GetRelatedRecordService.java`,
    `BulkDeleteRecordService.java`, `UpdateRecordService.java`,
    `CreateRecordService.java`, `VerifyTwoFactorAuthService.java`,
    `DeleteRecordService.java`）、`msg.err.web.invalidTableName`
    （`TableNameValidator.java`）、`msg.err.common.db.tsvValueContainsReservedMarker`
    （`TsvValueEscaper.java`）は、いずれも例外種別（`BusinessRuleViolationException`）は
    正しかったため変更せず、キー名のみ`msg.warn.*`に改名。
### issue #23: SELECT文のORDER BYを主キーのみに修正

- issue: https://github.com/freedomRemains/taskall-v2/issues/23
- PR: https://github.com/freedomRemains/taskall-v2/pull/24
- 本プロジェクトでは主キーは必ずサロゲートキーであるというルールがあるため、
  `SelectSqlBuilder.build`が生成するSELECT文のORDER BY句は、全カラムではなく
  主キー（`TBL_DEF.KEY_DIV=PRI`のカラム）のみで組み立てるよう修正
  （`src/main/java/com/freedom/taskall_v2/common/db/SelectSqlBuilder.java`）。
- 主キー定義が見つからない場合は、TBL_DEF資材自体の不整合と判断し
  `ApplicationInternalException`（`msg.err.common.db.primaryKeyNotFound`）をスローする。
- `DbSchemaSqlGeneratorRealDataTest`を実行し、`src/main/resources/db/sql`配下の
  `SELECT_*.sql`を再生成する必要がある（SQL生成ロジック変更時の定型作業）。

### issue #25: 「documents/rules/1000002_issue_points.md」追記のルール化

- issue: https://github.com/freedomRemains/taskall-v2/issues/25
- issue対応完了時に本ファイル（`documents/rules/1000002_issue_points.md`）へポイントを
  追記する運用を、`.github/copilot-instructions.md`の「開発の進め方」節にルールとして
  明文化した。

### issue #27: AWS環境構築（方針検討）

- issue: https://github.com/freedomRemains/taskall-v2/issues/27
- PR: https://github.com/freedomRemains/taskall-v2/pull/28
- 本issueは方針検討のみとし、具体的な実装（Terraformコード・GitHub Actions設定ファイル作成等）は
  後続の複数issueに分割して対応する。成果物は
  `documents/design/2000007_aws_build_up.md`にまとめた。
- 決定事項の要点:
  - 費用最小構成（EC2単体）を維持しつつ、CloudFront+ACM（無料HTTPS化）、WAF最小ルール
    （Managed Rule Core/SQLi/XSS＋IPレート制限）、EC2はPublic SubnetだがSecurity Groupで
    CloudFrontのIPレンジのみ許可し直アクセス禁止、SSHは使わずSSM Session Manager経由運用とする。
  - EC2インスタンスは`t4g.small`（Graviton, ARM64）、OSは`Amazon Linux 2023 (arm64)`。
  - DBはAWS環境でも当面SQLiteのまま構築し、RDS移行は将来課題とする（スコープ外）。
  - CI/CDはdevelop→mainマージ時のみGitHub Actionsを起動し、Gradleビルド＋全テスト→
    アーティファクトをS3へアップロードするところまでとする（feature→developのマージ時は
    現行通りCIを実行しない）。GitHub ActionsからAWSへの認証は、長期IAMユーザではなく
    **OIDC連携（GitHub Actions → AWS IAM Role）** を採用する（討議の結果、長期キー方式から
    変更）。
  - S3にアーティファクトがあるかどうかの判定・実際のリリース実行はEC2側が担う
    （costを抑えるため）。EC2側は**systemdタイマーで定期的にS3をポーリング**し、
    S3オブジェクトのメタデータ（バージョンIDまたはタイムスタンプ）を保持して新旧を差分検知する
    方式とする。
  - Terraformは`infra/terraform`配下に配置し、現時点ではprod環境のみ（tfvars分離なし）。
    state管理はS3＋DynamoDB Lockを使用し、publicリポジトリにstateファイルを絶対に含めない。
  - ドメイン ~~「www.taskall-v2.co.jp」~~ 「taskall-v2.com」 はAWS Route53で新規取得する想定だが、
    Terraformで管理するのはRoute53 Hosted Zoneのみとし、取得自体は手動で行う。
  - 監視（CloudWatchアラーム）、SQLiteのバックアップ、ステージング環境分離は、いずれも
    初期構築のスコープ外とし、将来必要になった時点で別issueとして検討する。
  - publicリポジトリでIaC資材を管理する前提として、`.gitignore`に`*.tfvars`を追加、
    `terraform validate`・`tflint`・`checkov`をCIに組み込み、PR時に自動lintを走らせる方針。
    access_key/secret_key/password等の秘匿情報混入の有無は、マージ時に必ずレビューする。

### issue #29: Terraform基盤（VPC / EC2 / Security Group / IAM Role）構築

- issue: https://github.com/freedomRemains/taskall-v2/issues/29
- PR: https://github.com/freedomRemains/taskall-v2/pull/31
- `documents/design/2000007_aws_build_up.md`（issue #27）で検討したTerraform構成のうち、
  VPC / EC2 / Security Group / IAM Roleの4モジュールを`infra/terraform`配下に実装した。
  CloudFront / ACM / Route53 / WAFは本issueのスコープ外で、別issueにて後続対応する。
- ディレクトリ構成: `infra/terraform/bootstrap`（state管理用S3+DynamoDB Lock、local state）、
  `infra/terraform/prod`（VPC/EC2/SG/IAM Roleをmodules経由で構築するroot module）、
  `infra/terraform/modules/{vpc,security_group,iam_ec2_role,ec2}`。
- 手順は`documents/procedure/3000021_terraform.md`にまとめた
  （bootstrap実行 → prod用`backend.conf`作成 → prod実行 → destroy時は逆順）。
- Security Group（CloudFront管理プレフィックスリスト`com.amazonaws.global.cloudfront.origin-facing`
  のみ許可）はEC2への直アクセス・SSHを禁止し、SSM Session Manager経由でのみ運用操作を行う構成。
- 実装・動作検証時に判明した留意点:
  - AWSの`aws_security_group.description`・`aws_vpc_security_group_ingress_rule`/
    `egress_rule`の`description`は、EC2 APIの仕様上**ASCII文字のみ対応**（日本語等の
    非ASCII文字を入れると`InvalidParameterValue`エラーになる）。該当箇所は英語表記とし、
    日本語の説明はコメントとして別途記載する（`modules/security_group/main.tf`）。
    一方、Terraform変数/output用の`description`（`variables.tf`/`outputs.tf`側）は
    AWSに送信されないメタ情報のため、日本語のままで問題ない。
  - ドメイン取得先の変更に伴い、`documents/rules/1000002_issue_points.md`（issue #27の節）
    ・`documents/design/2000007_aws_build_up.md`の取得予定ドメインを
    「www.taskall-v2.co.jp」から「taskall-v2.com」に修正済み。Route53での新規取得は完了済み。
  - `prod`構成の`apply`ではCloudFrontを構築しないため、取得済みドメインはこの時点では
    まだ使用しない（EC2にはElastic IPで直接アクセスする状態）。ドメインが実際に
    紐付くのはCloudFront/ACM/Route53を構築する後続issue対応時。

### issue #32: CloudFront / ACM / WAF / Route53構築

- issue: https://github.com/freedomRemains/taskall-v2/issues/32
- PR: https://github.com/freedomRemains/taskall-v2/pull/33
- issue #29に続き、`documents/design/2000007_aws_build_up.md`で検討したTerraform構成のうち、
  CloudFront / ACM / WAF / Route53の4モジュールを`infra/terraform/modules`配下に実装した。
  `terraform apply`によるAWS環境への実際の構築・`destroy`によるクリーンアップ、AWSコンソールでの
  Route53/CloudFront設定確認まで動作検証済み。
- 追加モジュール構成:
  - `modules/acm`: CloudFront用ACM証明書をDNS検証で発行（CloudFrontの仕様上
    us-east-1リージョン必須のため、`prod/main.tf`に`aws.us_east_1`のprovider aliasを追加し、
    `configuration_aliases`経由で`acm`/`waf`モジュールに渡す）。
  - `modules/waf`: WAFv2 WebACL（CLOUDFRONT scope、同じくus-east-1必須）。AWS Managed Rule
    （Core/SQLi/KnownBadInputs=Log4j対策）＋IPレート制限の最小構成。
  - `modules/cloudfront`: EC2をカスタムオリジンとするディストリビューション（HTTPS終端、
    動的アプリ用にキャッシュ無効化、WAF/ACM証明書をアタッチ）。
  - `modules/route53`: 取得済みドメインの既存Hosted Zoneを参照し、CloudFrontへの
    Alias(A/AAAA)レコード、およびCloudFrontオリジン用のAレコード（後述）を作成。
- 実装・動作検証時に判明した留意点（いずれも実機の`terraform apply`エラーから判明）:
  - WAFv2 WebACLの`description`は、EC2 Security Groupの`description`とは異なる独自の
    正規表現制約（`^[\w+=:#@/\-,\.][\w+=:#@/\-,\.\s]+[\w+=:#@/\-,\.]$`）を持ち、
    **丸括弧`()`が使用不可**（`ValidationException`）。`modules/waf/main.tf`のdescriptionから
    括弧を除去し、区切りをハイフン・カンマに変更した。
  - CloudFrontのカスタムオリジン`domain_name`には**IPアドレスを直接指定できない**
    （`InvalidArgument: The parameter origin name cannot be an IP address.`）。
    そのため`module.ec2.public_ip`を直接渡すのではなく、EC2のElastic IPを指す専用の
    Aレコード（`origin.<domain_name>`、`modules/route53`内で作成）を用意し、CloudFrontは
    このDNS名をオリジンとして参照する。一時的に手動でRoute53へ「www.taskall-v2.com」の
    Aレコードを作成しオリジンに指定する対応が取られたが、Terraform管理外のリソースに
    IaC側が依存する（EC2再作成時にIP追従できない等）ため差し戻し、上記の
    Terraform管理下のAレコード方式に統一した。
  - モジュール間の依存関係として、`module.route53_zone`は`module.acm`（DNS検証用）より前段で
    Hosted Zoneを参照しつつ、同じモジュール内の頂点Aliasレコードは`module.cloudfront`の
    出力に依存する。Terraformの依存グラフはリソース単位で解決されるため、この構成でも
    循環参照にはならない（`zone_id`出力はdata sourceのみに依存し、cloudfront側の変数を
    使う頂点Aliasレコードとは無関係なため）。
  - checkovの新規指摘（`CKV_AWS_374`/`CKV_AWS_305`/`CKV_AWS_310`/`CKV_AWS_86`/
    `CKV2_AWS_32`/`CKV2_AWS_31`/`CKV2_AWS_47`(誤検知)/`CKV2_AWS_23`(誤検知)）は、
    費用最小方針・モジュール境界による誤検知として、該当ファイル内にコメントで理由を明記した。

### issue #34: GitHub Actions CI/CD構築（OIDC設定含む）

- issue: https://github.com/freedomRemains/taskall-v2/issues/34
- PR: https://github.com/freedomRemains/taskall-v2/pull/35
- issue #27で検討したCI/CDフロー（develop→mainマージ時のみ起動、OIDC連携、S3への
  アーティファクトアップロードまでをCI/CD側が担い、実際のデプロイはEC2側のポーリングに任せる）を
  実装した。
- 追加したTerraformモジュール:
  - `infra/terraform/modules/github_oidc_role`: `token.actions.githubusercontent.com`を
    Issuerとする`aws_iam_openid_connect_provider`と、AssumeRole用の`aws_iam_role`を追加。
    信頼ポリシーの`sub`クレームを`repo:<owner>/<repo>:ref:refs/heads/main`に限定し、
    feature/developブランチや他リポジトリからのAssumeRoleを禁止。付与する権限もアーティファクト
    バケットへの`s3:PutObject`/`s3:GetObject`/`s3:ListBucket`のみに絞った最小権限とした。
  - `infra/terraform/modules/artifact_bucket`: CI/CDアーティファクト(jar)保存用のS3バケット。
    `bootstrap`のstate用バケットと同様、バージョニング・SSE-S3暗号化・パブリックアクセス
    ブロック・旧バージョン自動削除ライフサイクルを設定。EC2側は本バケットのバージョンIDを
    ポーリングし新旧差分を検知する想定（EC2側の実装は本issueのスコープ外、別issueで対応）。
  - `infra/terraform/prod/main.tf`に上記2モジュールを組み込み、`artifact_bucket_name`・
    `github_actions_role_arn`をoutputsに追加した。両出力値はGitHub側のRepository Variableとして
    手動設定が必要（`documents/procedure/3000031_github_actions_cicd.md`参照）。
- 追加したGitHub Actionsワークフロー:
  - `.github/workflows/cicd.yml`: `main`ブランチへの`push`（develop→mainマージ）のみで起動。
    `build-and-test`ジョブでGradleビルド・全テストを実行し、`upload-to-s3`ジョブで
    `aws-actions/configure-aws-credentials`によるOIDC認証を行いS3へjarをアップロードする
    （`permissions: id-token: write`が必須）。
  - `.github/workflows/terraform-lint.yml`: `infra/terraform/**`を変更するPull Requestに対し、
    `terraform fmt -check` → `terraform validate`（bootstrap/prod個別に`-backend=false`）→
    `tflint` → `checkov`の順でIaC静的チェックを実行する（issue #27の「防護措置・予防措置」節に
    対応）。
- Terraform CLI自体がAI作業環境（サンドボックス）に未インストールのため、`terraform apply`に
  よる実機構築・ワークフローの実機起動確認は未実施。`checkov`のみローカルで実行し、新規追加
  リソースの指摘が既存の`bootstrap`バケットと同種の許容済みリスクのみであることを確認した。
- PRレビュー指摘への対応:
  - 手順書`documents/procedure/3000022_github_actions_cicd.md`は、手順書が10番飛ばし採番
    （3000001, 3000011, 3000021, ...）である規約に反していたため、
    `documents/procedure/3000031_github_actions_cicd.md`にリネームした。
  - CI上の`terraform-lint.yml`実行結果から、`prod/main.tf`の`cloudfront`モジュール呼び出し部分
    （コメントで代入群が分断され、`terraform fmt`の整列規則とズレていた）で`terraform fmt`
    エラーが発生していたことが判明。AI作業環境にTerraform CLI(1.5.7)を一時インストールし、
    `terraform fmt -recursive -check -diff`で再現・修正を確認した。
  - CI上の`terraform validate`実行結果から、`modules/github_oidc_role`の
    `aws_iam_openid_connect_provider.thumbprint_list`にハードコードしていた値が
    実際には39文字（SHA1サムプリントとして必要な40文字に対し1文字不足）であったことが判明し、
    `Error: expected length of thumbprint_list.0 to be in the range (40 - 40)`エラーとなった。
    GitHubのOIDCエンドポイント証明書は発行元CA（本調査時点ではLet's Encrypt、以前はDigiCert）が
    将来変更されうるため、固定値のハードコードは失効・変更時に追従漏れのリスクがあると判断し、
    `data "tls_certificate"`（`hashicorp/tls`プロバイダ）でGitHubのOIDCエンドポイントの証明書
    チェーンを都度取得し、そのSHA1サムプリントを動的に使用する方式に変更した
    （`terraform init`・`terraform validate`をAI作業環境で実行し、`hashicorp/tls`プロバイダの
    追加インストール・`aws_iam_openid_connect_provider`の生成が成功することを確認済み）。
