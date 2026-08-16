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
