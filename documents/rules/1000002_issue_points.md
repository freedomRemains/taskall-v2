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
- AI作業環境（サンドボックス）にはTerraform CLI・tflint・checkovいずれも標準では未インストール
  だったため、`wget`でzipを取得し`python3`の`zipfile`モジュールで展開する方法
  （`curl`/`unzip`が利用不可なため）でTerraform CLI(1.5.7)・tflint(0.64.0)を一時インストールし、
  `terraform fmt`/`terraform validate`/`tflint --recursive`をローカルで実行できるようにした
  （`checkov`は元々インストール済み）。`terraform apply`による実機構築・ワークフローの実機起動
  確認は環境上未実施。
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
  - CI上の`tflint --recursive`実行結果から、`terraform_required_version`（`required_version`
    未設定）・`terraform_required_providers`（`aws`プロバイダのバージョン制約未設定）の警告が
    20件発生していたことが判明。これは本issueで新規追加した`artifact_bucket`/`github_oidc_role`
    だけでなく、issue #29・#32で構築済みの`vpc`/`security_group`/`iam_ec2_role`/`ec2`/`acm`/
    `waf`/`cloudfront`/`route53`の全モジュールが、モジュール単体では`required_version`・
    プロバイダバージョン制約を持たない構成だったために発生していた（ルート`prod`/`bootstrap`側は
    元々設定済みだったが、tflintは各モジュールディレクトリ単位でもこれらの設定を要求するため）。
    本issueで新設した`terraform-lint.yml`により初めてtflintがCIで実行されるようになったため、
    既存モジュール分もあわせて全モジュールに`required_version = ">= 1.5.0"`・
    `required_providers.aws.version = "~> 5.0"`（`acm`/`waf`は既存の`configuration_aliases`と
    併記）を追加し、20件の警告すべてを解消した。AI作業環境にtflint(0.64.0)を追加インストールし
    `tflint --recursive --chdir infra/terraform`が0件で完了することを確認済み。
  - CI上の`checkov`実行結果から、既存コードに`# [許容リスク: CKV_XXX]`として人間向けコメントで
    レビュー済み・許容合意済みのはずの20件が、いずれも`FAILED`として検出されていたことが判明。
    これは`terraform-lint.yml`の`checkov-action`が`soft_fail: false`のため、機械可読な抑制設定が
    無ければ許容済みリスクであってもCIが失敗する仕様のため。対応方針についてユーザーへ
    「警告のみでコードが妥当な場合はCI側を成功扱いにしてよい」との提案を受けたが、
    `soft_fail: true`のようなグローバル抑制は今後の新規findingsも一律で見逃してしまいCIの
    セキュリティゲートとしての意味を弱めるため採用せず、`#checkov:skip=CHECK_ID:reason`による
    リソース単位の抑制コメントを、既存の`[許容リスク]`コメントに追加する形で20件すべてに付与した
    （これにより将来の新規findingsは引き続きCIで検知される）。付与の過程で、checkov 3.3.9では
    `#checkov:skip=`コメントを`resource`宣言の**上（コメント群と並べる位置）ではなく、
    `resource "..." "..." {`ブロックの内側（開き波括弧の直後の行）に置く必要がある**ことが
    判明した（当初はブロック上に配置しており、ローカル検証で`Skipped checks: 0`のまま
    反映されない不具合として発覚。`/tmp`上の最小再現構成で位置による挙動差を確認し、
    ブロック内側へ移設することで解消）。修正対象は`bootstrap/main.tf`
    （`aws_s3_bucket.terraform_state`/`aws_dynamodb_table.terraform_lock`）、
    `modules/artifact_bucket`（`aws_s3_bucket.artifact`）、`modules/cloudfront`
    （`aws_cloudfront_distribution.app`、6件）、`modules/ec2`（`aws_instance.app`）、
    `modules/security_group`（`aws_security_group.ec2`）、`modules/vpc`（`aws_vpc.main`）、
    `modules/waf`（`aws_wafv2_web_acl.cloudfront`）、`modules/route53`
    （`aws_route53_record.origin`）の計20件。AI作業環境で`checkov -d infra/terraform`を
    再実行し`Passed checks: 112, Failed checks: 0, Skipped checks: 20`を確認、あわせて
    `terraform fmt`/`tflint`/`terraform validate`（bootstrap/prod双方）も再確認済み。
  - develop→mainマージ後、実際に`cicd.yml`のOIDC認証ステップで
    `Error: Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity`
    が発生。信頼ポリシー・OIDCプロバイダの設定内容自体はAWS側で確認しても想定通り
    （`sub`条件: `repo:freedomRemains/taskall-v2:ref:refs/heads/main`、`aud`条件:
    `sts.amazonaws.com`）であり、IAMリソース伝播遅延を疑い再実行を依頼したが再現。
    原因切り分けのため、AWSを呼ばずGitHub Actionsが発行するOIDCトークン自体をデコードして
    `sub`/`aud`等のクレームを出力する一時診断ジョブ(`debug/oidc-claims`ブランチ)を追加し
    実行したところ、実際の`sub`クレームが
    `repo:freedomRemains@188358132/taskall-v2@1313485636:ref:refs/heads/debug/oidc-claims`
    という、単純な`repo:owner/repo:ref:...`ではなく、owner名・repo名に不変ID(`@<owner_id>`/
    `@<repo_id>`)が付与された形式になっていることが判明した。これはGitHubがOrganization/
    リポジトリ名のリネームに伴うなりすまし対策として、リネーム履歴のあるリポジトリの`sub`
    クレームにこの形式を用いる仕様のためで、Terraformの信頼ポリシーが単純な文字列比較
    （`repo:${var.github_repository}:ref:refs/heads/${var.github_branch}`）だったことが
    直接の原因だった。リネームの影響を受けない`repository_id`/`repository_owner_id`
    クレーム(不変ID、AWS公式推奨)を条件に用いる方式へ変更し、`sub`条件は
    `repo:*:ref:refs/heads/${var.github_branch}`というブランチ限定のワイルドカードのみに
    緩和した。`modules/github_oidc_role`の`github_repository`変数（文字列比較用）は不要になり
    削除、代わりに`github_repository_id`/`github_repository_owner_id`変数を追加した
    （値は診断ジョブのOIDCトークン出力から取得: repository_id=1313485636,
    repository_owner_id=188358132）。診断用の一時ジョブ・ブランチ(`debug/oidc-claims`)は
    原因判明後に削除し、修正は`fix/oidc-repository-rename`ブランチとして別PRで対応した。
    AI作業環境で`terraform fmt`/`terraform validate`/`tflint`/`checkov`を再実行し
    いずれも合格することを確認済み（`tflint`は変数削除に伴い未使用変数警告が新たに1件
    発生したため、`prod/variables.tf`側の対応する`github_repository`変数も削除して解消）。
    実際のAWS環境への`terraform apply`・GitHub Actions上でのAssumeRole成功確認はユーザー側で
    実施予定。

---

### issue #39: EC2デプロイスクリプト構築 / issue #41: デフォルトアカウント認証情報のSSM経由差し替え

- issue #39: https://github.com/freedomRemains/taskall-v2/issues/39
- issue #41: https://github.com/freedomRemains/taskall-v2/issues/41
- PR #40（`feature/39`→`develop`）: https://github.com/freedomRemains/taskall-v2/pull/40
- PR #42（`feature/41`→`feature/39`、issue #41対応。#40へ取り込み後にマージ）:
  https://github.com/freedomRemains/taskall-v2/pull/42
- 関連手順書: `documents/procedure/3000041_ec2_deploy_scripts.md`
- issue #39では、EC2上でのリリース資材ポーリング・展開（systemdタイマー、`release.sh`、
  `flock`による多重実行防止）、`taskall-v2.service`、DBバックアップ（EC2ローカル+S3の二重化、
  直近10世代のみローカル保持）、CloudWatch Logs連携（logrotate、CloudWatch Agent）を実装した。
  詳細は`infra/ec2/`配下のスクリプト・`infra/terraform/modules/ec2`を参照。
- issue #39のPR #40レビュー時に、以下2件のセキュリティ課題が発覚し、issue #41として別途対応した。
  1. シードデータ(`src/main/resources/db/data/ACCNT.txt`)の全5アカウント
     （guest/gnruser/cmpnyuser/master/grandmaster）が、同一のbcryptハッシュ
     （平文パスワード「password」）を共有していた。
  2. ログイン後、DBメンテナンス画面から任意のテーブルデータを編集できてしまうため、
     上記の共有パスワードのまま本番リリースすると、特権アカウント（grandmaster等）経由で
     任意のDBデータを改ざんされ得る。
  - 対応方針として、移植元「remainz」の`DbUpdateBySqlFileService`（SQLファイルをS3経由で
    配布・実行）方式も検討したが、秘匿情報をファイルとして一切残さないベストプラクティスとして、
    アプリ本体のJava処理（`ApplicationRunner`）がAWS SDK経由でSSM Parameter Store
    （SecureString）から直接値を取得する方式を採用した。
- **issue #41実装の要点**（`com.freedom.taskall_v2.common.db.DefaultAccountCredentialInitializer`）:
  - `@Order(2)`の`ApplicationRunner`。`DbInitializer`（`@Order(1)`、シードデータ投入）の
    **後**に実行させる必要があるため、明示的にOrderで順序制御している。
  - `taskall.credential-init.enabled=true`の環境（本番EC2）でのみ動作
    （`@ConditionalOnProperty`）。ローカル開発・単体テストでAWS認証情報無しでも支障が
    出ないよう、既定値は`false`（`custom-prod.yaml`でも`${TASKALL_CREDENTIAL_INIT_ENABLED:false}`
    という既定`false`の環境変数展開にしている点に注意。`SecurityConfigProdProfileTest`
    （`@ActiveProfiles("prod")`）がAWS未設定環境でも通るようにするための必須の配慮）。
  - パスワードは`{parameterPrefix}/{アカウント種別}/password`
    （既定`parameterPrefix`は`/taskall-v2/accnt`）から取得し、ログイン照合と同一の
    `BCryptPasswordEncoder`Beanでハッシュ化してDBへ反映する。冪等性は「現在のPASSWORDが
    既知のデフォルトハッシュと一致するか」で判定し、既に変更済みなら上書きしない。
    SSM未設定の場合は、デフォルトパスワードのまま本番稼働することを防ぐため、
    アプリの起動自体を失敗させる（`ApplicationInternalException`、フェイルセーフ設計）。
  - PR #42レビューで追加要望を受け、メールアドレス（`MAIL_ADDRESS`）も同じ仕組みで
    `{parameterPrefix}/{アカウント種別}/mailAddress`から差し替え可能にした。ただし
    パスワードと異なり**SSM設定は必須ではない**（未設定ならシードのメールアドレスのまま
    起動を継続する）。実際に受信可能な本物のメールアドレスをGitHub上のリポジトリに
    一切コミットせずに設定できるようにするための任意項目のため。冪等性はパスワードとは
    独立に判定する（現在のMAIL_ADDRESSが既知のデフォルト値と一致するかどうか）。
  - SMTPメール接続情報（`spring.mail.*`）は、Springコンテキスト起動**前**に環境変数として
    存在する必要があるため、`ApplicationRunner`では扱えず、EC2側スクリプト
    `render-secrets-env.sh`（`taskall-v2.service`の`ExecStartPre`で毎回実行）が
    `/${project_name}/mail/{host,port,username,password}`から取得し
    `/etc/taskall-v2/secrets.env`（パーミッション600）へ書き出す方式とした。初回起動時のみ
    `init.sh.tftpl`が直接1回実行し、`EnvironmentFile`必須参照による起動失敗を防いでいる。
  - IAM（`ssm:GetParameter`/`ssm:GetParameters`）は`/${project_name}/*`配下に限定済みのため、
    `/taskall-v2/accnt/*`・`/taskall-v2/mail/*`とも追加のIAM/Terraform変更は不要だった。
- **本番リリース手順上の重要な順序制約**: SSM Parameter Storeへのパラメータ登録は、
  **Terraformのplan/apply（EC2起動）よりも先に完了させる必要がある**。EC2の初回起動
  （cloud-init）時点で`render-secrets-env.sh`実行・`taskall-v2.service`起動が走るため、
  SSM未登録のままapplyすると初回起動時に失敗する。運用担当者が実施すべき登録項目：
  - `/taskall-v2/accnt/{guest,individual,corporate,master,grandmaster}/password`（必須、5件全部）
  - `/taskall-v2/accnt/{guest,individual,corporate,master,grandmaster}/mailAddress`
    （任意、メールアドレスを変更したいアカウントのみ）
  - `/taskall-v2/mail/{host,port,username,password}`（必須、全部）
  - いずれもSecureStringとして登録する。
- ブランチ運用: `feature/41`は`develop`ではなく`feature/39`から分岐し、PR #42も
  `feature/39`へマージする（`feature/39`側のEC2初期化スクリプトの変更に依存するため）。
  PR #42マージ後にPR #40（`feature/39`→`develop`）をマージする、という2段階の統合順序。

---

### issue #48: 初回本番リリース試行時に起きた問題への対応

- issue #48: https://github.com/freedomRemains/taskall-v2/issues/48
- 関連: `documents/rules/1000003_trouble_points.md`（issue #48セクション参照、原因・対応の詳細記録）
- issue本文（terraformで`user_data_replace_on_change = true`を明示する）と、コメント
  （EC2初期構築時にJavaが未インストールだった）の2件を対応した。
  1. `infra/terraform/modules/ec2/main.tf`の`aws_instance.app`に
     `user_data_replace_on_change = true`を追加。以後`init.sh.tftpl`の変更は
     `terraform plan`時点でインスタンス再作成（force replacement）が必要な変更として
     検出できるようになる。
  2. `infra/ec2/init/init.sh.tftpl`の`dnf install`対象に
     `java-21-amazon-corretto-headless`を追加。`taskall-v2.service`が参照する
     `/usr/bin/java`が確実に存在するようにした。
- AI作業環境にはterraform/tflintバイナリが存在しないため、`init.sh.tftpl`を
  ダミー値でPythonレンダリングした上で`bash -n`構文検証・バイト数計測（6,208バイト、
  16KB上限内）、`checkov -d infra/terraform`（`Passed checks: 141, Failed checks: 0`）で
  妥当性確認を行った。実機の`terraform plan`/`apply`確認はユーザ側で実施予定。

---

### issue #51: 本番リリース後、ヘルスチェックがルートパス404により常に失敗する問題の対応

- issue #51: https://github.com/freedomRemains/taskall-v2/issues/51
- 前提: issue #48対応後の実機`terraform apply`でEC2上のSpringBootアプリ自体は正常起動したが、
  直後から`No static resource  for request '/'.`（`NoResourceFoundException`）が

  連続してアプリログに記録される事象が発生した。
- 原因: `infra/ec2/release/release.sh`の`health_check()`が、ルートパス`http://127.0.0.1:${APP_PORT}/`
  に対して`curl --fail`していたが、本アプリは全画面が`/taskall-v2/service/*.html`配下にあり
  `/`に対応する`@GetMapping`が存在しないため、常に404となりヘルスチェックが失敗し続けていた
  （`HEALTH_CHECK_RETRIES`回のリトライのたびに404アクセスがログへ記録される）。
- 対応: `TaskallV2Controller`にDB/業務ロジックに一切依存しない専用のヘルスチェック用
  エンドポイント`GET /healthz`（固定で`200 OK`のプレーンテキストを返すのみ）を追加し、
  `release.sh`の`health_check()`のcurl対象を`/healthz`に変更した。`SecurityConfig`は
  既に`anyRequest().permitAll()`のため、認証設定側の変更は不要だった。
- 設計判断: 本プロジェクトは「コントローラは`TaskallV2Controller`1つのみ」という方針だが、
  ヘルスチェックはDBレコード駆動のURI_PATTERN/SCR機構とは無関係な純粋なインフラ用途のため、
  同一クラス内に`handleRequest`を経由しない専用メソッドとして追加し、方針を維持しつつ
  対応した。
- 検証: `./gradlew test`（`TaskallV2ControllerTest`に`healthzは業務ロジックを呼び出さずOKを返すこと`
  テストを追加、`RequestHandlingService`が呼ばれないことを`verifyNoInteractions`で確認）に加え、
  実際に`./gradlew bootRun`でアプリを起動し、`GET /healthz`が`200 OK`を返すこと・`GET /`が
  （想定通り）404/500になることを実機相当の環境で確認した。`bash -n`による`release.sh`の
  構文検証も実施済み。

---

### issue #54: EC2デプロイスクリプト(release.sh等)の恒久的な自己更新の仕組みを追加する

- issue #54: https://github.com/freedomRemains/taskall-v2/issues/54
- 前提: issue #51対応(S3上のrelease.sh修正)後、実機で`terraform apply`を実行したが、
  既に起動済みのEC2インスタンスには反映されず、SSM Session Manager経由の手動`aws s3 cp`
  再取得が必要になった。これは`infra/ec2/init/init.sh.tftpl`がEC2初回起動(cloud-init)時にのみ
  スクリプトをS3から取得する設計(issue #39/#44)だったことに起因する恒久的な設計ギャップ
  だったため、別途issue化して対応した。
- 対応内容:
  1. `infra/ec2/init/files/update-ec2-scripts.sh`を新設。`release.sh`・`backup_common.sh`・
     `render-secrets-env.sh`・自分自身(`update-ec2-scripts.sh`)をS3(`EC2_SCRIPTS_PREFIX`)から
     再取得し上書きする「自己更新」スクリプト。
  2. `taskall-v2.service`・`taskall-v2-release.service`・`taskall-v2-backup.service`それぞれの
     `ExecStartPre`として`update-ec2-scripts.sh`を追加(既存の`render-secrets-env.sh`と同じ
     パターン)。これにより、各サービスの起動・実行のたびに最新版へ自動更新される
     (`taskall-v2-release.service`は5分間隔、`taskall-v2-backup.service`は毎日、
     `taskall-v2.service`は起動・再起動のたび)。
  3. `infra/ec2/init/init.sh.tftpl`の`config.env`生成箇所に`EC2_SCRIPTS_PREFIX`を追加し、
     `update-ec2-scripts.sh`が実際のS3プレフィックスを解決できるようにした。
     `infra/terraform/modules/ec2/main.tf`の`ec2_script_files`マップにも
     `update-ec2-scripts.sh`を追加(S3への事前アップロード対象に含める)。
- スコープ外(将来課題): systemdユニット定義自体(`*.service`/`*.timer`)や
  CloudWatch Agent設定・logrotate設定の自動更新は、`daemon-reload`やタイマー再起動を伴い
  影響範囲が大きいため、今回は対象外とした。必要になった時点で別issueとする。
- 検証: `bash -n`による全対象シェルスクリプトの構文検証、`init.sh.tftpl`のダミー値
  レンダリング(6,550バイト、16KB上限内)、`checkov -d infra/terraform`
  (`Passed checks: 141, Failed checks: 0`)、`./gradlew test`全成功を確認。
  Javaコードの変更は無い。実機の`terraform apply`確認はユーザ側で実施予定。

---

### issue #57: NoResourceFoundExceptionの応答を404に是正し、ログレベルを下げる

- issue #57: https://github.com/freedomRemains/taskall-v2/issues/57
- 前提: issue #51対応後、実機で`https://taskall-v2.com/wp-admin/install.php`のような、
  WordPress脆弱性スキャナ等のボットによる無差別アクセスに対し、`NoResourceFoundException`が
  `GlobalExceptionHandler`のcatch-all(`handleUnexpectedException`)に落ち、常にERRORログ・
  スタックトレース・500応答となっていた。本アプリはWordPress/PHPではなく実害はないが、
  高頻度なボットスキャンでCloudWatch Logsの容量・コストを圧迫し、本当に見るべきエラーログが
  埋もれる懸念があった。
- 対応: `GlobalExceptionHandler`に`NoResourceFoundException`専用の`@ExceptionHandler`を追加。
  - `@ResponseStatus(HttpStatus.NOT_FOUND)`で本来の404を返すようにした。
  - ログは`logger.debug(...)`とし、既定のログレベルでは出力されないようにした。
  - `e`自体をロガーに渡さず、メッセージ文字列(`e.getHttpMethod()`/`e.getResourcePath()`のみ)を
    記録することで、将来ログレベルを引き上げて出力するようになった場合でも、スタックトレースは
    出力されないようにした(`messages.properties`に`msg.debug.web.staticResourceNotFound`を追加)。
- 検証: `GlobalExceptionHandlerTest`に、`/wp-admin/install.php`へのアクセスで404・
  `error`ビューが返却されることを確認するテストを追加。`./gradlew test`全成功、
  `./gradlew bootRun`での実機相当環境での動作確認(404応答・ERRORログ非出力)も実施済み。

---

### issue #59: 二段階認証エラー(SSM認証情報未反映・リダイレクトURL不正・ログインデフォルト値除去)

- issue #59: https://github.com/freedomRemains/taskall-v2/issues/59
- 本番環境での二段階認証試行時に、3つの独立した問題が発覚した。
  1. **一次認証エラー(SSM Parameter Store関連)**: `infra/ec2/init/init.sh.tftpl`の
     `config.env`生成部で`TASKALL_CREDENTIAL_INIT_ENABLED=true`の設定が漏れており
     (issue #41実装時のコメントでは設定される想定だったが、実装が伴っていなかった)、
     `DefaultAccountCredentialInitializer`(SSM Parameter Store経由のデフォルトアカウント
     認証情報差し替え)が常に無効(既定値false)のまま起動し、シードデータの初期パスワードの
     ままだったため、SSMに設定した本番用パスワードでログインしようとすると一次認証に
     失敗していた。`init.sh.tftpl`へ`TASKALL_CREDENTIAL_INIT_ENABLED=true`を追加して解消。
     `user_data`(init.sh.tftpl)の内容変更のため、`user_data_replace_on_change=true`
     (issue #48)により`terraform apply`時にEC2が確実に再作成され反映される。
  2. **リダイレクトURLが`origin.taskall-v2.com`になる不具合**: CloudFrontはHTTP(S)接続の
     オリジン(EC2)への転送時、常にHostヘッダーをオリジンのドメイン名
     (`origin.taskall-v2.com`)へ書き換える仕様のため、SpringBoot側
     (`AccountAuthenticationFailureHandler#onAuthenticationFailure`の
     `response.sendRedirect(...)`等)が組み立てる絶対URLがオリジン向けドメインになって
     しまい、ブラウザから直接アクセスできない(名前解決できない)URLへリダイレクトされていた。
     - 対応: `infra/terraform/modules/cloudfront/main.tf`のオリジン設定へ
       `custom_header`で`X-Forwarded-Host`(実際の公開ドメイン名)・`X-Forwarded-Proto: https`を
       追加。SpringBoot側は`application-prod.yaml`に
       `server.forward-headers-strategy: framework`を追加し、これらのヘッダーを解釈して
       正しい外部向けURLを組み立てるようにした。
  3. **ログイン画面のデフォルト値除去**: `src/main/resources/templates/parts/common/
     20030_commonLogin.html`のメールアドレス・パスワード入力欄に、開発時の動作確認用として
     特権管理者(`grandmaster@account.com`/`password`)の値が`value`属性としてハードコードされて
     いたため、空文字へ変更した(本番環境で誰でもその値を閲覧・悪用できてしまう状態だったため)。
- 検証: `./gradlew test`全成功、`checkov -d infra/terraform`(141 passed/0 failed)、
  `bash -n`によるテンプレート構文チェック、Pythonでのダミー値による`init.sh.tftpl`レンダリング
  (4,733 bytes、16KB上限内)で`TASKALL_CREDENTIAL_INIT_ENABLED=true`が出力に含まれることを確認。

---

### issue #63: SSM Parameter Storeのプレフィックス不整合(taskall-v2 vs taskallv2)を修正する

- issue #63: https://github.com/freedomRemains/taskall-v2/issues/63
- issue #59対応・マージ後、本番環境で`DefaultAccountCredentialInitializer`が
  `ApplicationInternalException: SSM Parameter Storeにデフォルトアカウントのパスワード
  パラメータが設定されていません。parameterName=/taskall-v2/accnt/guest/password`で
  起動失敗する問題が発生した。
- 原因: IAMポリシー(`infra/terraform/modules/iam_ec2_role/main.tf`)は
  `arn:aws:ssm:*:*:parameter/${var.project_name}/*`(`project_name`のデフォルトは
  `taskallv2`、ハイフンなし)に限定して`ssm:GetParameter`を許可しており、メール接続情報
  取得(`render-secrets-env.sh`)の`SSM_PREFIX`(`/${PROJECT_NAME}/mail`)もこれと整合していた。
  一方、アプリ側のデフォルト値(`CredentialInitProperties.java`の`parameterPrefix`、
  `custom-prod.yaml`の`parameter-prefix`のデフォルト展開値)だけが`/taskall-v2/accnt`
  (ハイフンあり)になっており、IAMポリシーが許可する`/taskallv2/*`と一致していなかった。
- 対応: アプリ側のデフォルト値を`/taskallv2/accnt`(ハイフンなし)へ修正し、IAM・メール接続
  情報取得のプレフィックスと整合させた。関連するテスト
  (`DefaultAccountCredentialInitializerTest`/`CredentialInitPropertiesTest`/
  `AwsSsmParameterFetcherTest`)・設計書(`documents/design/2000007_aws_build_up.md`)・
  手順書(`documents/procedure/3000041_ec2_deploy_scripts.md`)の記載も併せて修正した。
- 検証: `./gradlew test`全成功。

---

### issue #66: 二段階認証パスコードメール送信で送信元(From)アドレス未設定によりSESから拒否される

- issue #66: https://github.com/freedomRemains/taskall-v2/issues/66
- issue #63対応・マージ後、一次認証(パスワード)は成功するようになったが、本番環境で
  二段階認証パスコードのメール送信時に`ApplicationInternalException: 二段階認証パスコード
  のメール送信に失敗しました`が発生し、実際の原因は
  `SMTPSendFailedException: 554 Message rejected: Email address is not verified.`
  (送信元が`root@ip-10-0-1-193.ap-northeast-1.compute.internal`)だった。
- 原因: `TwoFactorMailService#sendPasscode`が`SimpleMailMessage#setTo`のみ呼び出し、
  `setFrom`を一度も呼び出していなかった。送信元未設定時、JavaMailSender/Jakarta Mailは
  OS/JVMの既定値(実行ユーザー名@ホスト名)を自動生成するため、EC2のインスタンス内部
  ホスト名を含むアドレスが送信元になっていた。AWS SESはサンドボックス状態では送信元・
  宛先ともに検証済みアドレスであることを要求するため、この未検証の送信元アドレスで
  拒否されていた(宛先自体はAWSコンソール上で検証済みだったため、送信元側の問題と判明)。
- 対応: `src/main/java/.../common/config/MailProperties.java`を新設し(`CredentialInitProperties`
  と同様の`@ConfigurationProperties(prefix = "taskall.mail")`パターン)、
  `taskall.mail.from-address`(デフォルト`no-reply@taskall-v2.com`)を追加。
  `TwoFactorMailService`のコンストラクタへ`MailProperties`を注入し、`sendPasscode`内で
  `message.setFrom(mailProperties.getFromAddress())`を呼び出すよう修正した。
  `custom-prod.yaml`/`custom-local.yaml`にも`taskall.mail.from-address`設定を追加し、
  本番環境では`TASKALL_MAIL_FROM_ADDRESS`環境変数で上書き可能にした。
  SESはドメイン(`taskall-v2.com`)単位で検証済みのため、`no-reply@taskall-v2.com`は
  実在するメールボックスを別途用意しなくても送信元として利用可能(SESはDNSによる
  ドメイン所有権のみ検証し、ローカルパートの実在性は問わないため)。
- 検証: `TwoFactorMailServiceTest`に送信元アドレスの検証を追加、`MailPropertiesTest`を新設。
  `./gradlew test`全成功。

---

### issue #69: 「パスワードを忘れたら」機能の追加

- issue #69: https://github.com/freedomRemains/taskall-v2/issues/69
- PR #70（`feature/69`→`develop`）: https://github.com/freedomRemains/taskall-v2/pull/70
- 関連パス:
  - `src/main/java/com/freedom/taskall_v2/web/service/PasswordResetService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/StartPasswordResetService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/VerifyPasswordResetService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/PasswordResetMailService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/PasswordResetCleanupScheduler.java`
  - `src/main/java/com/freedom/taskall_v2/web/util/PasswordStrengthValidator.java`
  - `src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java`
  - `src/main/resources/db/data/TBL_DEF.txt`
  - `src/main/resources/db/data/{URI_PATTERN,HTML_PAGE,HTML_PARTS,PARTS_IN_PAGE,PARTS_ITEM,SCR,SCR_ELM,HTML_PARTS_IN_APROLE,GNR_KEY_VAL}.txt`
  - `src/main/resources/templates/parts/{10140_passwordResetInputMail.html,10150_passwordResetPasscode.html}`
  - `src/main/resources/templates/parts/common/{20030_commonLogin.html,20140_commonPasswordResetInputMail.html,20150_commonPasswordResetPasscode.html}`
  - `src/test/java/com/freedom/taskall_v2/web/service/{PasswordResetServiceTest,StartPasswordResetServiceTest,VerifyPasswordResetServiceTest,PasswordResetMailServiceTest,PasswordResetCleanupSchedulerTest}.java`
- 実装要点:
  - `PASSWORD_RESET`テーブルを新設し、`(SESSION_ID, MAIL_ADDRESS)`複合一意制約・`FAIL_CNT`・`IS_LOCKED`・`EXPIRES_AT`を保持する。
  - 1画面目はメールアドレス/新パスワード/確認用パスワードを受け付け、既存の同一メールアドレス行がロック中かつ期限内なら拒否、期限切れまたは未ロックなら削除して新規受付する。
  - パスワード強度は「数字・英大文字・英小文字・記号を全て含む8文字以上」で検証し、エラー文言は`GNR_KEY_VAL`＋`ErrMsgService`で表示する。
  - 2画面目は`PENDING_PASSWORD_RESET_ID`と`SESSION_ID`の整合性を確認し、`accountExists && passcodeMatches`を単一条件として扱って失敗時の`FAIL_CNT`加算を1 POSTあたり1回だけにしている。
  - メールアドレス不存在時でも`PasswordEncoder.matches(...)`を実行し、6桁コード照合処理のタイミング差で存在可否を推測されにくくした。
  - パスワード更新成功時は`ACCNT.PASSWORD`を更新し、`PASSWORD_RESET`行を物理削除する。
  - `PasswordResetCleanupScheduler`を`LoginStatusCleanupScheduler`と同じ`@Scheduled(initialDelay = 10 * 60 * 1000, fixedRate = 10 * 60 * 1000)`で追加した。
  - `db/data`変更後は`DbSchemaSqlGeneratorRealDataTest`で`db/sql`を再生成し、検証は`rm -f taskallv2.db && ./gradlew test`で実施した。

---

### issue #72: 本番DB更新の仕組み追加（Flyway導入）

- issue #72: https://github.com/freedomRemains/taskall-v2/issues/72
- 関連パス:
  - `src/main/java/com/freedom/taskall_v2/common/db/DbBootstrapState.java`
  - `src/main/java/com/freedom/taskall_v2/common/db/FlywayMigrationService.java`
  - `src/main/java/com/freedom/taskall_v2/common/db/FlywayMigrationRunner.java`
  - `src/main/java/com/freedom/taskall_v2/common/db/DbInitializer.java`
  - `src/main/java/com/freedom/taskall_v2/common/db/DefaultAccountCredentialInitializer.java`
  - `src/main/resources/db/flyway/V2__add_password_reset.sql`
  - `src/main/resources/application.yaml`
  - `build.gradle`
  - `src/test/java/com/freedom/taskall_v2/common/db/{DbBootstrapStateTest,FlywayMigrationServiceTest,FlywayMigrationRunnerTest,DbInitializerTest}.java`
- 背景: issue #69（パスワードを忘れたら）のマージ後、既存の本番DB（TBL_DEF/ACCNT等
  既にレコードが存在する）へスキーマ・マスタデータ差分だけを安全に反映する手段が
  存在しなかった。`db/data`/`db/sql`は新規DBの初回ブートストラップ専用の資材であり、
  差分だけを当てる用途には使えない。当初「db/update」配下にDROP/CREATE/INSERT SQLを
  置き、実行後にファイル削除する案が検討されたが、当該ファイルはjarにパッケージされる
  クラスパスリソースであり、アプリがランタイムに削除してもgit管理下のソースは
  変わらず、再デプロイ/再起動の度に再実行されてしまう(データ破壊のおそれ)ため、
  この案は採用しなかった。
- 採用した設計: Flyway（`org.flywaydb:flyway-core`、SpringBoot4.1.0の
  dependency-managementプラグインが`12.4.0`を管理、依存先はJackson3系`tools.jackson`の
  ためプロジェクトの既存スタックと整合し追加のJSONライブラリ不要）を導入。
  - マイグレーションファイルは`src/main/resources/db/flyway`配下に`V2__xxx.sql`から
    配置する(「V1」はFlyway導入前の状態を表す暗黙のベースラインとして予約)。
  - SQLiteは`flyway-database-sqlite`のような専用モジュールが無く、
    `FluentConfiguration#communityDBSupportEnabled(true)`によるコミュニティDB
    サポートで動作する(公式サポート対象DBには影響しないフラグのため、将来のMySQL
    移行後もそのままでよい)。
  - SpringBootの自動Flyway起動(`FlywayAutoConfiguration`)は`application.yaml`の
    `spring.flyway.enabled: false`で無効化し、`FlywayMigrationService`が独自に
    `Flyway`インスタンスを構築・実行する。
  - 既存の`DbInitializer`(`@Order(1)`、初回ブートストラップ)はそのまま残し、
    `FlywayMigrationRunner`(`@Order(2)`)をその後段に追加、
    `DefaultAccountCredentialInitializer`は`@Order(2)`→`@Order(3)`に変更した
    (ACCNTスキーマ変更を含むマイグレーション適用後にパスワード差し替えを行うため)。
  - ベースライン化はDBの状態に応じて2パターンに分岐する(`DbBootstrapState`で
    `DbInitializer`から`FlywayMigrationService`へ「この起動で新規作成したか」を
    伝達): (1)既存DB(Flyway導入前の本番DB等)は「V1」としてベースライン化した上で
    未適用のマイグレーションを適用(`baselineOnMigrate=true`,
    `baselineVersion=1`)。(2)この起動で`DbInitializer`が最新スキーマとして
    新規作成したDB(開発環境等)は、`db/data`の最新資材を反映済みのため、発見できる
    最新バージョンとしてベースライン化する(マイグレーション二重適用によるテーブル
    重複エラーを防ぐため)。
  - `V2__add_password_reset.sql`には、issue #69のPR #70で追加された差分のみを
    (共有マスタテーブルは既存本番レコードと衝突しないよう新規追加行のみ)、
    `git diff`で特定した上で手動転記した。ACCNT.MAIL_ADDRESSへの一意制約追加は、
    SQLiteが既存カラムへの`ALTER TABLE ADD CONSTRAINT`をサポートしないため、
    `CREATE UNIQUE INDEX IF NOT EXISTS`で代替した(SQLite/MySQL双方で機能的に同等)。
- 検証: 実際に`bootRun`で(1)新規DB作成時にV2としてベースライン化されPASSWORD_RESET
  テーブルが存在すること、(2)2回目起動時にマイグレーションが再実行されず冪等である
  ことを確認。加えて`FlywayMigrationServiceTest`で実SQLite(一時ファイル)を使い、
  既存DBシナリオ(V1ベースライン化+V2適用)・新規ブートストラップシナリオ
  (最新バージョンでベースライン化しV2非実行)の両方を検証。`rm -f taskallv2.db &&
  ./gradlew test`全成功。

---

### issue #75: 具体的コンテンツ追加に先立っての準備及び方針策定

- issue #75: https://github.com/freedomRemains/taskall-v2/issues/75
- 本issueは実装作業ではなく、今後のサインアップ機能・トップ画面等の具体的コンテンツ
  追加に先立つ準備・方針策定を目的とするもの。以下3点を検討した。
- **1. サインアップ時の規約の準備**
  - 従来システム(taskall)の利用規約URL(https://www.taskall.co.jp/ankeninfo/s/AgreementForUseServlet )
    は本環境から直接参照できなかった(外部アクセス制限)ため、一般的なSaaS型サービスの
    利用規約構成をベースに、現状実装済みの機能(アカウント登録・二段階認証・パスワード
    再設定)を踏まえた利用規約の**たたき台(案文)**をissueコメントとして提示した
    (https://github.com/freedomRemains/taskall-v2/issues/75#issuecomment-5306037296 )。
  - 投稿データの利用許諾条項(運営者によるサービス運営目的での利用許諾)について、
    従来システムでは第三者による無断転載・転用の禁止が明記されていたとの指摘があり、
    正式策定時(専門家レビュー時)の確認事項として記録する。
  - 本案文はAIによるたたき台であり、そのまま本番公開可能な完成品ではない。Pマーク
    (JIS Q 15001)取得を視野に入れる場合を含め、**弁護士・行政書士等の専門家によるレビュー
    を経てから正式公開すること**を必須とする。プライバシーポリシーは規約と独立した
    別文書として、専門家監修のもとで別途作成する。
- **2. 今後の作業指示について / 3. 作業指示への対応方針について**
  - 具体的コンテンツ追加が本格化するにつれ、実装水準の詳細に詳しくない依頼者から、
    大まかなUI・挙動の説明のみで改修依頼を受けることが多くなる見込み。曖昧な回答の
    繰り返し、同一機能への行きつ戻りつ、文脈と無関係な一言が実は新規要望である、
    といった統計的傾向への対応方針を検討した。
  - 検討結果は `.github/copilot-instructions.md` の「依頼者傾向への対応方針（issue #75）」
    セクションへ集約した。要点: 曖昧な回答には選択肢提示(`ask_user`のchoices)で対応、
    着手前に`1000002_issue_points.md`で蒸し返しを確認、文脈と無関係な一言は新規依頼か
    確認、部品化・共通化にこだわりすぎず依頼ごとに機械的な連番で画面部品/業務ロジックを
    追加してよい(複雑化した場合のみ共通化を検討)、古い資材は目安3ヶ月変更依頼が
    無ければ`@Deprecated`化して削除候補とする、着手前に規模感の一言を伝えてコスト
    認識を共有する。

---

### issue #78: サインアップ機能の実装

- issue #78: https://github.com/freedomRemains/taskall-v2/issues/78
- PR #79（`feature/78`→`develop`）: https://github.com/freedomRemains/taskall-v2/pull/79
- 関連パス:
  - `src/main/java/com/freedom/taskall_v2/web/service/SignUpService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/StartSignUpService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/VerifySignUpService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/SignUpMailService.java`
  - `src/main/java/com/freedom/taskall_v2/web/service/SignUpCleanupScheduler.java`
  - `src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java`
  - `src/main/resources/db/data/TBL_DEF.txt`
  - `src/main/resources/db/data/{URI_PATTERN,HTML_PAGE,HTML_PARTS,PARTS_IN_PAGE,PARTS_ITEM,SCR,SCR_ELM,HTML_PARTS_IN_APROLE,GNR_KEY_VAL}.txt`
  - `src/main/resources/db/flyway/V3__add_sign_up.sql`
  - `src/main/resources/msg/messages.properties`
  - `src/main/resources/templates/10000_contents.html`
  - `src/main/resources/templates/parts/{10160_signUpInput.html,10170_signUpPasscode.html}`
  - `src/main/resources/templates/parts/common/{20160_commonSignUpInput.html,20170_commonSignUpPasscode.html}`
  - `src/test/java/com/freedom/taskall_v2/web/service/{SignUpServiceTest,StartSignUpServiceTest,VerifySignUpServiceTest,SignUpMailServiceTest,SignUpCleanupSchedulerTest}.java`
- 位置づけ: 規約確定までサインアップの入口は公開しない方針のため、既存画面(マイページ・
  共通ヘッダ等)へサインアップへの導線(リンク)は一切追加していない。URLを直接叩けば動作する。
- 実装要点(issue #69「パスワードを忘れたら」を厳密なテンプレートとして踏襲):
  - `SIGN_UP`テーブルを新設し、`(SESSION_ID, MAIL_ADDRESS)`複合一意制約・`APROLE_ID`・
    `ACCOUNT_NAME`・`PASSWORD_HASH`・`PASSCODE_HASH`・`FAIL_CNT`・`IS_LOCKED`・`EXPIRES_AT`を保持する。
    `ACCOUNT_NAME`はissue本文の表には無いが、確定事項として1画面目でアカウント名入力欄を追加し
    一時保持するため独自に追加した(TBL_DEF `1002505`)。
  - サインアップ画面は「個人で登録(value=1)」「法人で登録(value=2)」をラジオボタン
    (`name=ACCOUNT_KIND`)で選択させ、バックエンドで`1→APROLE_ID=1000101`/`2→1000201`に変換する。
    画面にはID実値を出さず、`1/2`以外の改ざんパラメータは無言でトップ画面へリダイレクトする。
  - 1画面目(`StartSignUpService`): メールアドレスは`.toLowerCase()`で正規化。パスワード確認不一致は
    `GNR_KEY_VAL 1000406`、強度不足(`PasswordStrengthValidator`再利用)は`1000407`でエラー表示。
    `ACCNT`に同一メール既存なら`1000408`でマイページへ、`SIGN_UP`同一メール行がロック中かつ期限内なら
    `1000402`、それ以外(期限切れ/未ロック)は削除して新規受付。成功時は6桁コード入力画面へ遷移し
    `pendingSignUpId`をセッションに格納。
  - 2画面目(`VerifySignUpService`): `pendingSignUpId`空/該当行なし/セッションID不一致は無言でトップへ。
    ロック中(期限内)は`1000402`、期限切れは行削除しトップへ、未ロックかつ期限切れは15分ロック化し
    `1000402`。`ACCNT`に同一メール既存なら行削除し`1000409`(文言が1画面目の`1000408`と微妙に異なる)。
    6桁コード一致なら`ACCNT`+`APROLE_IN_ACCNT`をINSERTし`SIGN_UP`行削除、`signUpCompleted=true`で
    トップへ。不一致は`FAIL_CNT`加算、5回到達でロック(`1000402`)、未到達は`1000405`。
  - `SignUpService.createAccount`は`GeneratedKeyHolder`で採番した`ACCNT_ID`と`SIGN_UP.APROLE_ID`を
    使い`APROLE_IN_ACCNT`にも1行追加する。`findAccountByMailAddress`は`LOWER(MAIL_ADDRESS)=LOWER(?)`で比較。
  - `SignUpMailService`は件名「サインアップ確認」で6桁コードを送信、送信失敗時は作成した`SIGN_UP`行を
    削除して例外を再送出する。`SignUpCleanupScheduler`は`@Scheduled(initialDelay/fixedRate=10*60*1000)`。
  - `TaskallV2Controller`に`/taskall-v2/service/signUp.html`・`signUpPasscode.html`(各GET/POST)を追加し、
    `pendingSignUpId`のセッション引継ぎ・`signUpCompleted`フラグによるクリアを`pendingPasswordResetId`と
    同じパターンで実装した。
  - GNR_KEY_VALは既存の`1000402/1000405/1000406/1000407`を流用し、新規に`1000408`
    (signUpMailExistsFromSignUpError)・`1000409`(signUpMailExistsFromPasscodeError)を追加した。
  - 本番反映用に`db/flyway/V3__add_sign_up.sql`を追加(SIGN_UP CREATE + 各マスタテーブルの新規行INSERT)。
  - 既存の件数固定テスト2件を新テーブル・新マイグレーションに合わせて更新:
    `DbInitializationServiceTest`(25→26テーブル・770→828 INSERT)、`FlywayMigrationServiceTest`
    (新規ブートストラップ時のベースラインバージョンを`2`→`3`、SIGN_UPテーブル作成を追加)。
  - `db/data`変更後は`DbSchemaSqlGeneratorRealDataTest`で`db/sql`を再生成し、
    `rm -f taskallv2.db && ./gradlew test`で全270テスト成功を確認した。
