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
