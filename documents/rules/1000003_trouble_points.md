# 各issueのポイントまとめ

---

[READMEに戻る](../../README.md)

---

## 概要

本資料ではAIに単体検証まで依頼した(ある程度品質を担保しているはずの)資材を動かしたときに起きた、トラブルについて記録します。
AIに確認や対応をお願いし、時間やクレジットを使ってしまった内容を記録し、分析することで、次回以降のトラブルを軽減することを目標としています。

- AIに「1000003_trouble_points.mdへの追記をお願いします。」という依頼があったら、本資料の末尾に`###`見出しでセクションを追加してください。
    - 「テンプレート」の項に従い、「概要」「原因」「対応」「防御策」の4項を記述してください。
    - 防御策は「プルリクマージ依頼の前に、1000002_issue_points.md記述済みか確認する」といったように、具体的な手順を記述してください。
    - 防御策を実行するためにプロジェクト内の資料を読んで考えるような書き方は回避してください。(かえってクレジットを使ってしまうためです)
    - 事前に予測できない類のトラブルなどで防御策がない場合は、簡潔に理由を書いて「～のため、防御策なし」としてください。(これも考えてクレジットを使うことを回避するための措置です)
- 「.github/copilot-instructions.md」にも本資料の概要を記載します。本資料を読み、issue対応の最終確認時に、過去の類似トラブルと同様の問題が起きる要因がないか、防御策が適用できているか確認してください。

---

### テンプレート

1. 概要

    - (起きたトラブルの概要を記述します)

2. 原因

    - (起きたトラブルの原因を記述します)

3. 対応

    - (起きたトラブルへの対応内容を記述します)

4. 防御策

    - (同様のトラブルが起きないように防御する方策があれば記述します)

---

### issue #44: EC2初期化スクリプト(`user_data`)のサイズ上限超過によるterraform planエラー

1. 概要

    - issue #39/#41対応（PR #40/#42）マージ後、`develop`→`main`マージに先立ちユーザが
      実機で`terraform plan`を実行したところ、`aws_instance.app`の`user_data`(`init.sh.tftpl`)に
      対し`Error: Invalid function argument`（`file()`が指定パスにファイルを検出できない）が
      複数発生した。
    - 上記エラーはユーザ側のローカル作業フォルダに`infra/ec2`ディレクトリ本体を配置していな
      かったこと（AIの作業ディレクトリと異なるローカルチェックアウト構成だったこと）が原因で、
      ユーザ自身の作業環境の設定漏れであり実装上の不具合ではなかった。
    - ユーザがファイル配置後に再度`terraform plan`を実行したところ、今度は
      `Error: expected length of user_data to be in the range (0 - 16384), got ...`という、
      AWSの`user_data`サイズ上限(16,384バイト)超過エラーが発生した。これは実装上の不具合
      （issue #44として起票）であることが判明した。

2. 原因

    - `infra/terraform/modules/ec2/main.tf`の`aws_instance.app`が、`init.sh.tftpl`内で
      `file()`関数を使い、systemdユニット定義・`release.sh`・`backup_common.sh`・
      `render-secrets-env.sh`・CloudWatch Agent設定等、複数のファイル本体をそのまま
      `user_data`へ埋め込んでいた。
    - PR #40（issue #39）・PR #42（issue #41）の実装・レビュー時点では、`terraform validate`・
      `tflint`・`checkov`はいずれも文法・セキュリティ面のチェックであり、`user_data`の
      実際のレンダリング後サイズ(AWS固有の16KB上限)までは検証していなかったため、
      埋め込みファイルの合計サイズが上限(約17.5KB)を超過していることに気づけなかった。

3. 対応

    - S3配置化(推奨案)を採用し、PR #45（issue #44）で対応した。
    - `infra/terraform/modules/ec2/main.tf`に`aws_s3_object.ec2_scripts`を新設し、EC2側
      デプロイスクリプト一式を事前に既存の`artifact_bucket`(`ec2-scripts/`プレフィックス配下)
      へアップロードするようにした。`release.sh`用に既に付与済みの`s3:GetObject`権限を
      再利用したため、追加のS3バケット・IAM変更は不要だった。`aws_instance.app`は
      `depends_on`でアップロード完了後に起動するよう保証した。
    - `infra/ec2/init/init.sh.tftpl`側は、各ファイルの`file()`埋め込みを廃止し、
      `fetch_ec2_script()`関数経由の`aws s3 cp`によるS3からの取得に置き換えた。
      レンダリング後のサイズは約17.5KB→約5.9KBに縮小した。
    - 対応の妥当性確認として、ダミー値で`init.sh.tftpl`をPythonでレンダリングした上で
      `bash -n`による構文検証・バイト数計測を行った。

4. 防御策

    - Terraformの`aws_instance`等、`user_data`に複数ファイルを`file()`/`templatefile()`で
      埋め込む変更を行う場合は、実装完了時にレンダリング後の合計バイト数を計測し、
      AWSの`user_data`サイズ上限(16,384バイト)を超過していないか確認する
      （実機の`terraform apply`を伴わずとも、ダミー値でのレンダリングとバイト数計測で
      事前に検出できるため、レビュー時のチェック項目として明示する）。
    - `terraform plan`のエラーメッセージが「操作対象のファイル配置に関するもの
      (`Invalid function argument`等)」なのか「AWS側の値の制約に関するもの
      (`Invalid value for ...`のバイト数範囲等)」なのかを、対応前に切り分けて確認する。
      前者はユーザのローカル作業環境固有の問題である可能性があるため、まずリポジトリの
      最新化・ディレクトリ構成の一致を確認してから、実装上の不具合として扱うかを判断する。

---

### issue #48: 初回本番リリース試行時に起きたJava未インストール・user_data変更未検知の問題

1. 概要

    - issue #39/#41/#44対応マージ後、実際にEC2へ初回本番リリースを試行したところ、
      `java`コマンドが存在せず（`which java`でも所在なし）、`taskall-v2.service`の
      アプリ起動（`ExecStart=/usr/bin/java ...`）が失敗する問題が発生した。
    - あわせて、`aws_instance.app`の`user_data`（`init.sh.tftpl`）を変更しても、
      Terraformの既定動作では既存EC2インスタンスに対し`user_data`属性の値のみ更新され、
      実際のcloud-init再実行（インスタンス再作成）が行われないため、
      `terraform plan`/`apply`時点で変更に気付けない・意図した初期構築処理が反映されない
      リスクがあることが判明した。

2. 原因

    - `infra/ec2/init/init.sh.tftpl`の`dnf install`対象に、CloudWatch Agent・SQLiteのみを
      指定しており、アプリ実行に必須のJavaランタイムのインストールが漏れていた。
      Amazon Linux 2023にはJavaが標準搭載されていないため、明示的なインストールが必要だった。
    - `infra/terraform/modules/ec2/main.tf`の`aws_instance.app`に
      `user_data_replace_on_change = true`を設定しておらず、`terraform plan`実行時に
      `user_data`変更が「インスタンス再作成が必要な変更」として警告されない状態だった。

### issue #80: db/data更新後にdb/sql再生成前提の件数固定テストがずれる問題

1. 概要

    - issue #80対応中、`DbInitializationServiceTest`のINSERT総数期待値を先に834へ更新したところ、
      まだ`DbSchemaSqlGeneratorRealDataTest`で`src/main/resources/db/sql`を再生成していない段階では
      実際のSQL件数が831のままで、単体テストが失敗した。
    - その後`DbSchemaSqlGeneratorRealDataTest`を実行して`db/sql`再生成後は、同テスト期待値834が
      正しい状態へ戻った。

2. 原因

    - 本プロジェクトでは`db/data`がSQL生成元、`db/sql`が生成物であり、`DbInitializationServiceTest`は
      実際には生成物側(`db/sql/*.sql`)の件数を検証している。
    - `db/data`だけ更新した直後に件数固定テストの期待値まで先行変更すると、生成物との一時的不整合で
      失敗する。

3. 対応

    - `DbSchemaSqlGeneratorRealDataTest`を先に実行して`db/sql`を再生成し、その後に
      `DbInitializationServiceTest`期待値を834へ確定させた。
    - フルテスト(`rm -f taskallv2.db && ./gradlew test`)で再生成後の状態を最終確認した。

4. 防御策

    - `src/main/resources/db/data/`を変更したissueでは、件数固定テストを更新する前に
      まず`./gradlew test --tests "com.freedom.taskall_v2.common.db.DbSchemaSqlGeneratorRealDataTest"`を
      実行して`db/sql`を再生成する。
    - `DbInitializationServiceTest`の件数を変更する前に、`src/main/resources/db/sql`内の
      INSERT件数を確認してから値を確定する。

3. 対応

    - `infra/ec2/init/init.sh.tftpl`の`dnf install`に`java-21-amazon-corretto-headless`を
      追加し、`taskall-v2.service`が参照する`/usr/bin/java`が確実に存在するようにした。
    - `infra/terraform/modules/ec2/main.tf`の`aws_instance.app`に
      `user_data_replace_on_change = true`を明示的に追加した。これにより、以後
      `init.sh.tftpl`を変更した場合、`terraform plan`時点でインスタンス再作成
      （force replacement）が必要な変更として検出できるようになる。
    - 対応の妥当性確認として、AI作業環境にterraform/tflintが存在しないため、
      `init.sh.tftpl`をダミー値でPythonレンダリングした上で`bash -n`による構文検証・
      バイト数計測（6,208バイト、16KB上限内）を行い、`checkov -d infra/terraform`で
      `Passed checks: 141, Failed checks: 0`を確認した。実機での`terraform plan`/`apply`
      確認はユーザ側で実施予定。

4. 防御策

    - EC2の`user_data`（cloud-init初期構築スクリプト）を変更する場合は、必ず
      `aws_instance`リソースに`user_data_replace_on_change = true`が設定されているか
      確認する（本対応で設定済みのため、以後は既存設定を維持していれば問題ない）。
    - `dnf install`等でインストールするパッケージを追加・変更する際は、systemdユニット
      （`ExecStart`等）が参照する実行コマンド（`java`等）が実際にそのパッケージで
      提供されているか、事前にAmazon Linux 2023のパッケージ内容を確認する。

---

### issue #59: 本番環境での二段階認証エラー(SSM反映漏れ・CloudFrontヘッダー転送・ログイン初期値)

1. 概要

    - issue #48/#51/#54対応マージ・実機反映後、実際に本番環境で二段階認証を試行したところ、
      正しいメールアドレス・パスワードのはずが一次認証エラーとなり、またログイン失敗時の
      リダイレクト先が`http://origin.taskall-v2.com/...`という、ブラウザから直接アクセス
      できない(名前解決できない)URLになる問題が発生した。あわせて、ログイン画面の入力欄に
      特権管理者(grandmaster)のメールアドレス・パスワードがデフォルト値としてハードコード
      されたままである点も発覚した。

2. 原因

    - issue #41でSSM Parameter Store経由のデフォルトアカウント認証情報差し替え機能
      (`DefaultAccountCredentialInitializer`)を実装した際、`taskall.credential-init.enabled`を
      本番のEC2デプロイでは`init.sh.tftpl`が`TASKALL_CREDENTIAL_INIT_ENABLED=true`を設定する
      前提のコメントを書いていたが、実際には`init.sh.tftpl`側にその設定を追加し忘れていた。
      このため本機能は常に既定値(false=無効)のまま起動しており、SSMに登録した本番用
      パスワードが一切反映されず、シードデータの初期パスワードのままだった。
    - CloudFrontはHTTP(S)接続のカスタムオリジンへの転送時、Hostヘッダーをオリジンの
      ドメイン名(`origin.taskall-v2.com`)へ常に書き換える仕様であるため、SpringBoot側が
      `HttpServletRequest`から絶対URLを組み立てる処理(`sendRedirect`等)が、利用者が実際に
      アクセスした公開ドメインではなく、オリジン向けの内部ドメインを基準にURLを生成して
      しまっていた。これはCloudFrontの標準的な既知の挙動だが、初期構築時のレビュー・検証では
      考慮されていなかった。
    - ログイン画面のデフォルト値は、開発時の動作確認の利便性のために設定したまま、本番向けの
      除去対応が漏れていた。

3. 対応

    - `infra/ec2/init/init.sh.tftpl`の`config.env`生成部に
      `TASKALL_CREDENTIAL_INIT_ENABLED=true`を追加した(issue #59)。
    - CloudFrontのオリジン設定(`infra/terraform/modules/cloudfront/main.tf`)へ
      `X-Forwarded-Host`/`X-Forwarded-Proto`のカスタムヘッダーを追加し、SpringBoot側
      (`application-prod.yaml`)へ`server.forward-headers-strategy: framework`を追加して、
      これらのヘッダーから正しい外部向けURLを組み立てるようにした。
    - `20030_commonLogin.html`のメールアドレス・パスワードのデフォルト値を空文字にした。

4. 防御策

    - **SSM等の外部パラメータストア連携機能を追加する際は、「機能を有効化するフラグ自体が
      実際にterraform側のuser_data/config.env生成コードに含まれているか」を、実装が
      完了した回のセルフレビューで、`grep`等により該当の環境変数名を`infra/`配下全体から
      横断検索し、参照側(アプリの`application-*.yaml`)と設定側(`init.sh.tftpl`等)の
      両方に存在することを確認する。片方の実装(コメントに書いた設定手順)だけで
      満足せず、実際に設定される行(`ENV_NAME=value`形式の代入)がコード上に存在するかを
      機械的に確認する。**
    - **CDN(CloudFront等)を新規に導入する、またはHTTP/HTTPS終端の構成を変更する際は、
      「オリジンへの転送時にHostヘッダー・スキームがどう書き換わるか」を実装前に
      AWS公式ドキュメントで確認し、アプリ側が絶対URLを組み立てる処理(リダイレクト、
      メール本文中のURL埋め込み等)がある場合は、X-Forwarded-*ヘッダーの転送・解釈
      (SpringBootなら`server.forward-headers-strategy`)をセットで検討・実装する。
      本プロジェクトでは初期構築時にこの観点のレビューが漏れていたため、今後CDN配下に
      置く新規サービス実装時は、この教訓をチェック項目として明示的に確認する。**
    - **本番向けにリリースするテンプレート・設定ファイルへ、開発時の動作確認用の
      認証情報や個人情報に類する値をハードコードする場合は、実装完了時に
      `grep -rn "value=\"" src/main/resources/templates`等で全テンプレートの
      デフォルト値埋め込み箇所を横断的に洗い出し、本番リリース前に除去漏れがないか
      確認する。**

---

### issue #69: 新規画面のDBデータ追加時に`PARTS_ITEM`と初期化件数の更新を漏らした

1. 概要

    - issue #69のパスワード再設定画面を追加した際、`SecurityConfigTest`で`/taskall-v2/service/inputMail.html`のGETが500となった。
    - あわせて、`DbInitializationServiceTest`の期待件数も古いままで、フルテスト時に件数不一致で失敗した。

2. 原因

    - `HTML_PAGE` / `PARTS_IN_PAGE` / `HTML_PARTS`などのDBデータは追加していたが、`10000_contents.html`や共通ヘッダが参照する`PARTS_ITEM`（少なくとも`systemName`と必要なエラーメッセージ項目）の追加を漏らしていた。
    - `DbInitializationServiceTest`は実際の`db/sql`件数に追従する必要があるが、`PASSWORD_RESET`テーブルと関連マスタ追加後の総実行件数へ更新していなかった。

3. 対応

    - `PARTS_ITEM.txt`へ、`inputMail.html`/`resetPasscode.html`用の`systemName`・`urlLink`・`errMsgList`定義を追加し、`DbSchemaSqlGeneratorRealDataTest`で`db/sql`を再生成した。
    - `DbInitializationServiceTest`の期待値を、25テーブル分のDROP/CREATEと770件のINSERTに合わせて更新した。

4. 防御策

    - 新規画面を追加する際は、`HTML_PAGE` / `HTML_PARTS` / `PARTS_IN_PAGE`だけでなく、`PARTS_ITEM`に`systemName`・必要な画面部品用`ITEM_KEY`が揃っているかを必ず確認する。
    - `db/data`を変更したら、`DbSchemaSqlGeneratorRealDataTest`の後に`DbInitializationServiceTest`と`rm -f taskallv2.db && ./gradlew test`を実行し、SQL実行件数のズレをその場で検出する。

### issue #80: EC2側スクリプト変更を伴うマージ後、terraform apply未実施のまま本番反映しクラッシュループが発生した

1. 概要

    - issue #80(reCAPTCHA対応)のdevelop→mainマージ後、本番のreCAPTCHAチェックボックスが表示されなかった。
    - ユーザーが調査のため`sudo systemctl restart taskall-v2`を手動実行したところ、EC2の`taskall-v2.service`が`update-ec2-scripts.sh`の`Permission denied`でクラッシュループ(再起動カウンタ70超)に陥った。
    - `terraform apply`を実施し、EC2側スクリプト(`render-secrets-env.sh`)一式をS3経由で更新して復旧した。

2. 原因

    - 今回のPRは`infra/ec2/init/files/render-secrets-env.sh`(EC2側スクリプト)を変更したが、このファイル本体はTerraform(`infra/terraform/modules/ec2/main.tf`の`aws_s3_object.ec2_scripts`)経由でS3へアップロードされ、EC2側は`update-ec2-scripts.sh`が起動のたびにS3から取得する仕組みになっている。develop→mainマージ時のCI/CD(`cicd.yml`)はアプリ本体jarのみをS3へアップロードし、EC2側スクリプトの更新は対象外(スコープ外)のため、`terraform apply`を別途実行しない限りS3上のスクリプトは更新されない。
    - `terraform apply`未実施のままユーザーが手動で`systemctl restart`したタイミングが、ちょうど5分間隔の`taskall-v2-release.timer`(release.sh経由の`update-ec2-scripts.sh`実行)と重なった。`update-ec2-scripts.sh`は`aws s3 cp`で自分自身を非アトミックに上書きするため、複数プロセスの同時実行により書き込み途中のファイルを別プロセスが実行しようとし、ファイルの実行権限が壊れる状態に陥った。

3. 対応

    - `taskall-v2-release.timer`を停止し、これ以上の競合を止めた。
    - 壊れた`update-ec2-scripts.sh`等をS3から手動で`aws s3 cp`により再取得・`chmod 750`し直し、`taskall-v2.service`を1回だけ再起動して復旧を確認した。
    - `terraform apply`を実施(誤った資材で一度実行していたため、正しい資材で再実行)し、S3上の`render-secrets-env.sh`が更新されたことを確認後、`taskall-v2.service`を再起動してreCAPTCHAキーが`secrets.env`に反映されることを確認した。
    - `taskall-v2-release.timer`を再開した。

4. 防御策

    - **`infra/ec2/**`配下のファイルを変更するPRをdevelop→mainマージする際は、マージ後に必ず`documents/procedure/3000021_terraform.md`の手順で`terraform plan`→`apply`を実行し、`aws_s3_object.ec2_scripts`に差分がないか確認する。** マージ直後にPRの説明欄へ「terraform applyが必要」である旨を明記する。
    - **`terraform apply`実行前後は、EC2側で`systemctl restart taskall-v2`等の手動操作を行わない。** `taskall-v2-release.timer`と競合するため、手動操作が必要な場合は先に`sudo systemctl stop taskall-v2-release.timer`でタイマーを止めてから行う。
    - `terraform apply`実行後は、`aws s3 cp s3://<artifact_bucket>/ec2-scripts/<変更したファイル名> - --region <region> | head`でS3側の内容が確実に更新されているか確認してから、EC2側の`systemctl restart taskall-v2`を実施する。

### issue #84: 既存の`TBL_DEF`テーブル定義を確認せず、`ATTR_GRP`/`ATTR`を重複追加してしまった

1. 概要

    - issue #84(案件一覧画面)の実装で`ATTR_GRP`/`ATTR`テーブルのデータ(`ATTR_GRP.txt`/
      `ATTR.txt`)を新規作成した際、`TBL_DEF.txt`にも両テーブルの列定義を新規追加した。
    - `DbSchemaSqlGeneratorRealDataTest`実行後、`rm -f taskallv2.db && ./gradlew test`を
      実行したところ、`TaskallV2ApplicationTests`等がSQLiteの
      `duplicate column name: ATTR_GRP_ID`エラーで軒並み失敗した。

2. 原因

    - `TBL_DEF.txt`には、design doc(issue #83)策定時点で`ATTR_GRP`/`ATTR`の列定義が
      既に(データ未投入のまま)登録済みだったが、実装時にその既存定義を確認せず、
      同じテーブル名で新しいIDブロック(1002601〜1002711)の列定義を追記してしまい、
      `CREATE_ATTR_GRP.sql`/`CREATE_ATTR.sql`に同一カラムが二重生成された。

3. 対応

    - `TBL_DEF.txt`から重複追加した`ATTR_GRP`/`ATTR`の列定義(1002601〜1002711)を削除し、
      既存定義(1002001〜1002114)のみを残した。
    - `ATTR_GRP.txt`/`ATTR.txt`のデータファイルは、既存の列定義(部分カラムのみで
      `NUM_MIN`/`NUM_MAX`/`ATTR_NOTE`等は未使用)にそのまま合わせられることを確認し、
      修正不要だった(`InsertSqlBuilder`はデータ行にあるカラムのみでINSERT文を
      生成するため、列定義側の全カラムを埋める必要はない)。

4. 防御策

    - **`db/data`へ新規テーブルのデータファイルを追加する前に、必ず`TBL_DEF.txt`を
      対象テーブル名で`grep`し、既存の列定義が(データ未投入のまま)登録済みでないか
      確認する。** 特に、設計検討issue(design doc追加のみのissue)で先行して
      テーブル定義だけ登録されているケースがあるため、実装issueで「新規テーブル」だと
      思い込まず、既存定義の有無を必ず確認する。
    - `db/data`変更後は`DbSchemaSqlGeneratorRealDataTest`→
      `rm -f taskallv2.db && ./gradlew test`の順で必ず実行し、`CREATE TABLE`の重複列
      エラーのような構造的な不整合をローカルで検出してから次の作業へ進む。

### issue #84(PR #85マージ後): db/data変更に対応するFlywayマイグレーションを作成せず、develop→mainマージ後も本番DBに反映されなかった

1. 事象

    - issue #84(案件一覧画面)のPR #85をdevelop→mainへマージ後、ユーザーから
      「案件情報のリンクが表示されない」と本番環境での不具合報告があった。
    - ユーザーは当初、直前に発生したissue #80のトラブル(EC2スクリプト変更を伴う
      マージ後、terraform apply未実施でクラッシュループ)を踏まえ、「今回もTerraformの
      init/plan/applyが必要か」と質問した。

2. 原因

    - issue #84の実装では`db/data`配下に新規テーブル(`ANKEN`/`ATTR_IN_ANKEN`)の
      作成、既存の`ATTR_GRP`/`ATTR`テーブルへのマスタデータ追加、および
      `URI_PATTERN`/`HTML_PAGE`/`HTML_PARTS`/`PARTS_IN_PAGE`/`HTML_PARTS_IN_APROLE`/
      `SCR`/`SCR_ELM`/`PARTS_ITEM`/`TBL_DEF`という9テーブルへの追加・更新を行ったが、
      これに対応する`db/flyway/V{n}__*.sql`マイグレーションファイルを一切作成しなかった。
    - 本プロジェクトの規約(`documents/design/2000001_base_design.md`)では、
      `db/data`/`db/sql`は新規(未初期化)DBのブートストラップ専用であり、既存の
      本番DBへ差分反映するには必ずFlywayマイグレーションが必要である。この規約を
      失念したまま実装・レビュー・マージが完了してしまった。
    - 今回の原因はissue #80(`infra/ec2/**`変更に伴うterraform apply漏れ)とは
      別種のデプロイ漏れであり、「マージ後に本番に自動反映されない変更がある」という
      構造的なパターンが2回連続で発生した形になる。

3. 対応

    - `src/main/resources/db/flyway/V6__add_anken_list.sql`を新規作成し、
      issue #84のPR差分から抽出した以下を1ファイルにまとめて反映した。
        - `ATTR_GRP`/`ATTR`(TBL_DEF定義は初期コミットから存在したが実テーブル未作成
          だったため`CREATE TABLE IF NOT EXISTS`が必要)、`ANKEN`/`ATTR_IN_ANKEN`
          (完全新規)の`CREATE TABLE IF NOT EXISTS`。
        - 上記4テーブルのマスタ・シードデータ`INSERT`(`db/sql/INSERT_*.sql`を流用)。
        - `TBL_DEF`への27行の列定義`INSERT`(`db/data/TBL_DEF.txt`の値を
          そのまま抽出。空文字とNULLの区別を`InsertSqlBuilder`と同じ規則に合わせる
          必要があった)。
        - `URI_PATTERN`/`HTML_PAGE`(POSTは最終決定通り`forward`/
          `10000_contents.html`)/`HTML_PARTS`/`PARTS_IN_PAGE`/
          `HTML_PARTS_IN_APROLE`/`SCR`/`SCR_ELM`/`PARTS_ITEM`(新規2行+既存17行の
          `UPDATE`)への`INSERT`/`UPDATE`。
    - 検証は「issue #84直前のコミット(`466a2bd`)でDBをブートストラップ→
      `taskallv2.db`をコピー→現行コードで起動しFlywayを適用」した結果と、
      「現行`db/data`でゼロから新規ブートストラップした結果」を主要13テーブルで
      突き合わせ、完全一致することを確認した。この過程で以下2件のバグを検出・修正した。
        - `PARTS_ITEM`のurlLink 17行の`VERSION`を`+1`していたが、実際は
          `db/data`側で2回(#84本体でOR句追加、追加改修でORDER BY追加)更新されて
          いたため`+2`が正しかった(`VERSION`不一致で発覚)。
        - `ATTR_GRP`/`ATTR`の`CREATE TABLE`がV6に含まれておらず、`FlywayMigrationServiceTest`
          の「既存DBにV1ベースライン→未適用マイグレーション適用」テストで
          `no such table: ATTR_GRP`エラーとなり発覚(こちらのテストの方が
          issue #84直前の本番相当の状態に近く、手動突き合わせだけでは検出できなかった)。
    - `FlywayMigrationServiceTest`のうち、最新バージョンを`5`と決め打ちしていた
      アサーションを`6`に修正した(新しいマイグレーション追加のたびに追従が必要な
      既知の固定値であり、issue #80のトラブル記録にある「件数固定テスト」と同種の
      注意点)。

4. 防御策

    - **`db/data`配下のファイルを1行でも追加・変更するPRは、必ず対応する
      `db/flyway/V{n}__*.sql`を同一PR内に含めることを必須のセルフチェック項目とする。**
      レビュー・マージ前に「このPRは`db/data`を変更したか」→「変更した場合、
      対応するFlywayファイルはあるか」を機械的に確認する。
    - Flywayマイグレーションを新規作成した場合は、`FlywayMigrationServiceTest`の
      ような既存DB相当のテストで実際に適用できることを確認する。可能であれば
      「変更前コミットでブートストラップ→マイグレーション適用」と「変更後の
      `db/data`でゼロからブートストラップ」の結果を突き合わせ、完全に一致することを
      確認する(本トラブル対応で実施した手順を今後のテンプレートとする)。
    - 新規テーブルをFlywayマイグレーションでCREATEする際は、そのテーブルの
      `TBL_DEF`登録が今回のissueで初めて追加されたのか、それとも過去のissueで
      定義のみ先行登録されていたのか(`git log`でデータファイルの初出コミットを
      確認)を必ず確認し、後者の場合も実テーブルが本番に存在するとは限らない前提で
      `CREATE TABLE IF NOT EXISTS`を用意する。

### issue #91: 本番環境で案件情報画面のページネーション/属性検索(POST)がシステムエラーになった(CSRFトークンがModel属性を上書き)

1. 概要

    issue #84の案件一覧画面(案件情報)実装後、develop→mainマージ・本番反映を行ったところ、
    案件一覧は正常表示されたが、ページネーションボタンや属性検索の「検索」ボタン(いずれも
    `ankenList.html`へのPOST)をクリックするとシステムエラーとなった。ローカル環境では
    再現しなかった。

2. 原因

    - `TaskallV2Controller#buildContext()`は、受信したリクエストパラメータを無条件に
      JSONコンテキストへコピーしていた。
    - 本番環境ではSpring SecurityのCSRF保護が有効(`SecurityConfig`の`local`プロファイルの
      みCSRFを無効化)なため、POSTフォーム送信時に`_csrf`という隠しパラメータが必ず
      送信される。ローカルはCSRF無効のため`_csrf`パラメータ自体が存在せず、本トラブルが
      再現しなかった。
    - `_csrf`パラメータもコンテキストJSONへコピーされ、`ScriptElementService`チェーンを
      素通りしたまま`TaskallV2Controller#populateModel()`でModelへ書き戻される際、
      Spring Securityが本来設定していた`CsrfToken`型の`_csrf`属性を、単なる文字列で
      上書きしてしまっていた。
    - 結果、`10000_contents.html`の`${_csrf.parameterName}`評価時に
      `SpelEvaluationException: Property or field 'parameterName' cannot be found on
      object of type 'java.lang.String'`が発生し、システムエラーとなった。

3. 対応

    - `TaskallV2Controller#buildContext()`で、リクエストパラメータをコンテキストへ
      コピーする際に`_csrf`を除外するよう修正した。
    - 回帰テストとして、POSTパラメータに`_csrf`を含めた場合でも、
      `RequestHandlingService.execute()`へ渡されるJSON文字列に`"_csrf"`が含まれない
      ことを検証するテストケースを追加した。

4. 防御策

    - **DB駆動のリクエストコンテキスト(`buildContext()`)へリクエストパラメータを
      機械的に転記する実装を新規に書く/変更する場合は、Spring Securityなど
      フレームワークが独自にModel属性を設定するパラメータ名(`_csrf`等)が
      混入しないか確認する。** 混入した場合、`populateModel()`側での無条件な
      Model上書きと組み合わさり、フレームワーク側の型付きオブジェクトが
      文字列で上書きされる。
    - **CSRF保護に依存する挙動(POSTフォーム送信・`_csrf`隠しフィールドを含む画面)は、
      ローカル環境(CSRF無効)だけでなく、本番相当にCSRFを有効化した状態でのテストも
      検討する。** 本トラブルはローカルでは`_csrf`パラメータ自体が送信されないため
      再現せず、本番でのみ顕在化した典型例。
