# EC2側デプロイスクリプト（systemdタイマーによるS3ポーリング・再起動）

---

[READMEに戻る](../../README.md)

---

## 概要

- 本資料は、`infra/ec2`配下の資材（EC2初期構築スクリプト・リリーススクリプト・
  バックアップスクリプト・systemdユニット定義）の構成と動作を説明します。
- 本資材は[issue #39](https://github.com/freedomRemains/taskall-v2/issues/39)（
  [issue #27](https://github.com/freedomRemains/taskall-v2/issues/27)の後続issue）に対応します。
- また、[issue #41](https://github.com/freedomRemains/taskall-v2/issues/41)
  （issue #39のレビューで判明した、デフォルトアカウントのパスワード共有・メール接続情報未設定
  リスクへの対応）に伴う変更もあわせて反映しています。デフォルトアカウントパスワードの
  差し替えはアプリ側のJava処理(`DefaultAccountCredentialInitializer`)、メール接続情報の
  取得はEC2側スクリプト(`render-secrets-env.sh`)がそれぞれAWS SSM Parameter Store経由で行います。
- Terraform資材自体の構築手順は
  [documents/procedure/3000021_terraform.md](3000021_terraform.md)を参照してください。
  本資材は`infra/terraform/modules/ec2`から`aws_instance.user_data`として自動的に注入されるため、
  運用者が手動でEC2にコピー・実行する必要はありません（EC2起動時にcloud-init経由で1回だけ
  自動実行されます）。

---

## ディレクトリ構成

```
infra/ec2/
  init/
    init.sh.tftpl                    # 初期構築スクリプト(Terraformのtemplatefile()でuser_dataとして
                                      # レンダリングされる。バケット名等のTerraform変数のみここで解決する)
    files/
      taskall-v2.service             # アプリ本体のsystemdサービスユニット
      taskall-v2-release.service     # リリースチェック用のoneshotサービスユニット
      taskall-v2-release.timer       # 5分間隔でtaskall-v2-release.serviceを起動するタイマー
      taskall-v2-backup.service      # 毎日3時の定期バックアップ用oneshotサービスユニット
      taskall-v2-backup.timer        # 毎日3時(JST)にtaskall-v2-backup.serviceを起動するタイマー
      logrotate.conf                 # /etc/logrotate.d/taskall-v2として配置するログローテート設定
      cloudwatch-agent-config.json   # CloudWatch Agent設定(ログ収集対象・Log Group名)
      render-secrets-env.sh          # SSM Parameter Storeからメール接続情報を取得しsecrets.envへ
                                      # 書き出すスクリプト(issue #41、taskall-v2.serviceのExecStartPre)
  release/
    release.sh                       # S3ポーリング・リリース実行・失敗時の自動ロールバック
    backup_common.sh                 # バックアップ処理の共通関数(release.sh・定期バックアップ双方から利用)
```

- `init.sh.tftpl`以外のファイルは、いずれも通常のシェルスクリプト・設定ファイルであり、
  Terraformの記法に依存しません（`shellcheck`等で単体検証できる状態を保っています）。
- `init.sh.tftpl`は、Terraform側の`ec2`モジュール(`infra/terraform/modules/ec2/main.tf`)が
  `templatefile()`でレンダリングし、`file()`で他の各ファイルの内容をそのまま埋め込みます。
  Terraform変数を解決する箇所は`init.sh.tftpl`内の`/etc/taskall-v2/config.env`書き出し部分のみに
  限定し、`release.sh`/`backup_common.sh`自体は静的な内容としています。

---

## EC2起動時の初期構築(`init.sh.tftpl`)の処理内容

1. タイムゾーンを`Asia/Tokyo`に設定する（ログのタイムスタンプ・毎日3時の定期バックアップを
   JST基準で解釈させるため。Amazon Linux 2023の初期状態はUTC）。
2. `amazon-cloudwatch-agent`・`sqlite`(DBバックアップのオンラインバックアップAPI用)を
   `dnf`でインストールする（AWS CLI v2はAmazon Linux 2023に標準搭載のため追加インストール不要）。
3. `/opt/taskall-v2/{history,bin}`・`/var/log/taskall-v2`・`/etc/taskall-v2`を作成する。
4. Terraform変数（プロジェクト名・リージョン・アーティファクト/バックアップ用バケット名・
   アプリポート番号等）を`/etc/taskall-v2/config.env`に書き出す。`release.sh`・
   `backup_common.sh`・`taskall-v2.service`はいずれも本ファイルを`EnvironmentFile`または
   `source`で読み込む。`SPRING_PROFILES_ACTIVE=prod`もここで設定し、アプリが
   `application-prod.yaml`（本番用DB接続・メール送信設定等）で起動するようにする
   （未設定のままだとデフォルトの`local`プロファイルで起動してしまう不備があったため、
   issue #41対応時に追加した）。
5. `release.sh`・`backup_common.sh`・`render-secrets-env.sh`を`/opt/taskall-v2/bin/`に配置し、
   `render-secrets-env.sh`を1回実行してメール接続情報(SSM Parameter Store由来)を
   `/etc/taskall-v2/secrets.env`(パーミッション600)へ書き出しておく（`taskall-v2.service`が
   起動時に本ファイルを`EnvironmentFile`として必須参照するため、リリースタイマーによる
   初回起動より前に用意しておく必要がある）。
6. systemdユニット・タイマー(`taskall-v2.service`・`taskall-v2-release.{service,timer}`・
   `taskall-v2-backup.{service,timer}`)を`/etc/systemd/system/`に配置する。
7. logrotate設定を`/etc/logrotate.d/taskall-v2`に配置する。
8. CloudWatch Agent設定を配置し、`amazon-cloudwatch-agent-ctl`で起動する。
9. `systemctl daemon-reload`後、`taskall-v2.service`は`enable`のみ行う（この時点ではjarが
   未配置のため起動しない。実際の初回起動は、後述のtaskall-v2-release.timerがEC2起動2分後に
   自動実行するrelease.shが担う）。`taskall-v2-release.timer`・`taskall-v2-backup.timer`は
   `enable --now`で即座に有効化する。

---

## リリースフロー(`release.sh`、5分間隔で`taskall-v2-release.timer`から起動)

1. `flock`による排他制御（cronタイマーの多重実行防止。systemdの同一Unit排他だけに頼らず、
   将来cronへ移行した場合や手動実行時にも安全なように、スクリプト自身でも排他制御を行う）。
2. `aws s3api head-object`でS3上の最新アーティファクト(`releases/taskall-v2.jar`)のバージョンID
   （S3バケットのバージョニング機能を利用）を取得する。
3. 前回デプロイ時に記録したバージョンID(`/opt/taskall-v2/.deployed_version`)と比較し、
   差分がなければ何もせず終了する。
4. 差分があれば、新バージョンのjarをダウンロードした上で、
   現行のjar・DB(`backup_common.sh`の`backup_app`関数)をローカル履歴ディレクトリと
   DBバックアップ用S3バケットの両方へバックアップする（ロールバック時の退避先を兼ねる）。
5. アプリを停止し、新jarに入れ替えて起動する。
6. ヘルスチェック（`systemctl is-active`かつアプリポートへの`curl`アクセス）に成功すれば、
   新しいバージョンIDを記録して完了とする。
7. ヘルスチェックに失敗した場合は、手順4でバックアップした旧jarへ自動的にロールバックし、
   再度ヘルスチェックを行う（issue #39の追加提案「リリース失敗時の自動ロールバック」に対応）。

---

## バックアップフロー(`backup_common.sh`)

- `backup_app`関数は、`release.sh`（リリース直前バックアップ）・毎日3時の
  `taskall-v2-backup.timer`（定期バックアップ）の双方から共通で呼び出される。
- DBのバックアップには、ファイルコピーではなくSQLiteのオンラインバックアップAPI
  (`sqlite3 <db> ".backup <dest>"`)を使用する。これにより、アプリを稼働させたままでも
  安全にバックアップを取得できる。
- **毎日3時の定期バックアップでは、アプリの停止・EC2の再起動は行わない。**
  issue #39での討議の結果、無応答対策としての定期再起動は対症療法であり、
  深夜3時の再起動と5分間隔のリリースポーリングが同時に走った場合の競合リスクもあるため、
  見送りとなった（CloudWatch Agent導入後の実際の監視結果を見て、必要であれば運用開始後に
  改めて検討する）。
- バックアップは、EC2ローカル(`/opt/taskall-v2/history/`)とDBバックアップ用S3バケットの
  両方に保存する。EC2ローカルのみでは、EC2自体の障害（ディスク故障等）で全データを失う
  リスクがあるため（issue #39の見直し提案）。
- ローカル履歴ディレクトリは直近10世代のみ保持し、それ以前は自動削除する（`prune_history`関数）。
  S3側は`backup_bucket`モジュールのライフサイクル設定（経過日数基準、デフォルト30日）で
  長期保持するため、ローカルディスクは直近分のみで十分と判断した。

---

## デフォルトアカウントパスワード・メール接続情報のSSM Parameter Store経由注入(issue #41)

- issue #39のレビューで、シードデータ(`src/main/resources/db/data/ACCNT.txt`)の
  デフォルトアカウント5件(guest/gnruser/cmpnyuser/master/grandmaster)が全て同一の
  bcryptハッシュ(平文パスワード「password」)を共有しており、かつログイン後は
  DBメンテナンス画面から任意のテーブルデータを操作できてしまうため、本番リリース前に
  必ず解消すべき課題として本対応を行った。
- **デフォルトアカウントパスワードの差し替え**は、アプリ本体のJava処理
  (`com.freedom.taskall_v2.common.db.DefaultAccountCredentialInitializer`、
  `ApplicationRunner`)が担う。
  - `taskall.credential-init.enabled=true`の環境（本番のEC2インスタンス）でのみ動作する
    （ローカル開発・単体テスト実行時はAWS認証情報が無くても支障が出ないよう、既定は無効）。
  - SSM Parameter Store(SecureString)の`{parameterPrefix}/{アカウント種別}/password`
    (デフォルトの`parameterPrefix`は`/taskall-v2/accnt`)から平文パスワードを取得し、
    アプリが通常のログイン照合に使う`BCryptPasswordEncoder`と同一のBeanでハッシュ化した上で、
    `RecordQueryService`/`JdbcTemplate`経由でACCNTテーブルへ`UPDATE`する。秘匿情報は
    JVMのメモリ上でのみ扱い、ディスク・S3等のファイルには一切書き出さない。
  - 冪等性は「対象アカウントの現在のPASSWORDが、シードデータ由来の既知のデフォルトハッシュと
    一致するかどうか」で判定する。既に本処理や管理者の手動変更でパスワードが変更済みの場合は
    上書きしない。
  - SSMパラメータが未設定の場合は、既知のデフォルトパスワードのまま本番稼働することを防ぐため、
    アプリの起動自体を失敗させる（`ApplicationInternalException`）。
  - `DbInitializer`(`@Order(1)`)がテーブル・シードデータを作成した**後**に実行される必要が
    あるため、本クラスには`@Order(2)`を付与している。
- **メール(SMTP)接続情報の取得**は、EC2側スクリプト`render-secrets-env.sh`が担う。
  - SpringBootのメール設定(`application-prod.yaml`の`spring.mail.*`)はコンテキスト起動時に
    `TASKALL_MAIL_*`環境変数を必要とするため、ApplicationRunner(コンテキスト起動後にしか
    実行できない)ではなく、`taskall-v2.service`の`ExecStartPre`(アプリ起動直前に毎回実行)で
    AWS CLIを使いSSM Parameter Store(`{project_name}/mail/{host,port,username,password}`)から
    値を取得し、`/etc/taskall-v2/secrets.env`(パーミッション600)へ書き出す。
  - 初回起動時は、リリースタイマーによる初回アプリ起動より前に`init.sh.tftpl`が
    `render-secrets-env.sh`を1回実行し、あらかじめ`secrets.env`を用意しておく
    （`taskall-v2.service`が本ファイルを`EnvironmentFile`として必須参照するため）。
  - 以降はサービスを`restart`するたびに最新のSSM値を再取得するため、jarの再デプロイ無しで
    メール接続情報のみをローテーションすることも可能。
- 運用担当者は、本番への初回リリース前に、以下のSSMパラメータ(SecureString、
  `/${project_name}/*`配下)をあらかじめ作成しておく必要がある。未作成の場合、
  上記いずれの仕組みもアプリ・サービスの起動自体を失敗させる（意図的なフェイルセーフ）。
  - `/taskall-v2/accnt/{guest,individual,corporate,master,grandmaster}/password`
  - `/taskall-v2/mail/{host,port,username,password}`

---

## ログ・CloudWatch Logsについて

- アプリ本体のログは`/var/log/taskall-v2/taskall-v2.log`に出力する（systemdの
  `StandardOutput=append:`機能を利用）。
- `release.sh`・`backup_common.sh`のログは、それぞれ`/var/log/taskall-v2/release.log`・
  `/var/log/taskall-v2/backup.log`に出力する。
- logrotateにより、1ファイルあたり100MB上限・日次gzip圧縮・過去30日分保持とする
  （`copytruncate`方式。アプリ側にログファイルの再オープン処理がないため）。
- CloudWatch Agentが上記3つのログファイルを、いずれもLog Group `/taskall-v2/application`
  （タグ`Name=taskall-v2-log`、保持期間365日。`infra/terraform/modules/ec2`で
  `aws_cloudwatch_log_group`として事前にTerraform管理する）へ、ログストリーム名
  `{instance_id}/app`・`{instance_id}/release`・`{instance_id}/backup`で送信する。

---

## IAM権限について

- `infra/terraform/modules/iam_ec2_role`にて、EC2ロールへ以下の権限を追加している。
  - `CloudWatchAgentServerPolicy`（AWS管理ポリシー）: CloudWatch Agentのログ・メトリクス送信用。
  - アーティファクト用S3バケットへの`s3:GetObject`/`s3:GetObjectVersion`/`s3:ListBucket`
    （リリーススクリプトのポーリング・ダウンロード用。書き込み権限は付与しない）。
  - バックアップ用S3バケットへの`s3:PutObject`/`s3:GetObject`/`s3:ListBucket`
    （バックアップスクリプトのアップロード用）。
  - `ssm:GetParameter`/`ssm:GetParameters`（`/${project_name}/*`配下のパラメータのみに限定）:
    以下2用途で使用する。
    - 特権管理者を含む全デフォルトアカウントのパスワードを、アプリ起動時のJava処理
      （`DefaultAccountCredentialInitializer`）が`/${project_name}/accnt/{アカウント種別}/password`
      から取得し、`BCryptPasswordEncoder`でハッシュ化した上でDBへ反映する(issue #41)。
    - メール(SMTP)送信の接続情報を、EC2起動前スクリプト`render-secrets-env.sh`が
      `/${project_name}/mail/{host,port,username,password}`から取得し、
      `/etc/taskall-v2/secrets.env`(パーミッション600)へ書き出す(issue #41)。
      SpringBootのコンテキスト起動前に環境変数として存在している必要があるため、
      アカウントパスワードとは異なりJava側ではなくEC2側スクリプトで処理する。
  - 上記いずれのSSMパラメータも、初回リリース前に運用担当者が事前に作成しておく必要がある
    （未設定の場合、`DefaultAccountCredentialInitializer`はアプリ起動自体を失敗させ、
    `render-secrets-env.sh`は`ExecStartPre`が失敗し`taskall-v2.service`が起動しない
    ―― いずれも「デフォルトパスワードのまま」「メール未設定のまま」の本番稼働を防ぐための
    意図的なフェイルセーフ設計)。

---

## 動作検証について

- AI作業環境（サンドボックス）にはAWS認証情報がなく、実機の`terraform apply`・EC2起動・
  実際のリリース/バックアップ動作の確認は未実施。
- ローカルでは以下を実施し、静的な正しさを確認した。
  - `terraform fmt -recursive -check -diff` / `terraform validate` / `tflint --recursive` /
    `checkov`（いずれもエラーなし）。
  - `init.sh.tftpl`をPythonスクリプトでダミー値展開し、`bash -n`で構文チェック
    （埋め込んだ`release.sh`/`backup_common.sh`/各systemdユニット/JSON設定も含め、
    ヒアドキュメントの二重展開等の問題がないことを確認）。
  - `release.sh`・`backup_common.sh`単体の`bash -n`・`shellcheck`（info/warningレベルの
    指摘のみ。動的な`source`パス・Terraformテンプレート変数由来の警告であり実害なし）。
  - `cloudwatch-agent-config.json`のJSON構文チェック。
  - `render-secrets-env.sh`単体の`bash -n`・`shellcheck`、および`init.sh.tftpl`への
    再埋め込み後の`bash -n`(issue #41対応分)。
  - `./gradlew test`（`DefaultAccountCredentialInitializerTest`・`AwsSsmParameterFetcherTest`・
    `CredentialInitPropertiesTest`を含む全単体テストが成功。`local`/`prod`両プロファイルで
    コンテキストを起動する既存テスト(`TaskallV2ApplicationTests`・`SecurityConfigProdProfileTest`)も、
    `taskall.credential-init.enabled`が既定でfalseのままのため、AWS認証情報が無い環境でも
    問題なく成功することを確認）。
- 実機での動作確認（EC2起動→初回リリース→ヘルスチェック→ロールバック→定期バックアップ、
  および実際のSSM Parameter Store経由でのパスワード・メール接続情報注入）は、
  実際にAWS環境へ`terraform apply`した際に別途実施する。
