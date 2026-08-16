# Copilot Instructions for taskall-v2

## プロジェクトの目的

`taskall-v2` は「**ローコードWebアプリ構築ツール**」です。画面遷移や業務ロジックの呼び出しを機能ごとにハードコードするのではなく、画面構成・ルーティング・権限の構造を**DBレコード**として設計し、Javaコード側は汎用的にそのレコードを解釈するエンジンとして実装します。全体のデータモデルは
`documents/design/2000002_model_design.md` を参照してください。ルーティングの流れは次の通りです。

```
URI_PATTERN（受信URL）
  -> HTML_PAGE（HTTPメソッドごとに、どのSCRを実行するか／応答種別／遷移先を定義）
    -> SCR（スクリプト） -> SCR_ELM（サービスクラスの実行順リスト。例: com.freedom.taskall_v2.web.service.GetAccountService）
      -> 各サービスはJSONを入出力とし、あるサービスの出力が次のサービスの入力になる
        -> HTML_PARTS / PARTS_IN_PAGE（再利用可能な画面パーツを組み合わせて最終的な画面を構成）
```

権限まわりもデータ駆動です。`ACCNT`（アカウント）と `APROLE`（ロール）は
`APROLE_IN_ACCNT` で紐づき、画面アクセス制御は `REQUIRE_APROLE`、画面パーツ単位の
read/edit 制御は `HTML_PARTS_IN_APROLE` で管理し、`AuthUtil.hasEditAuth(...)` /
`AuthUtil.hasReadAuth(...)` で判定します。このエンジン自体はremainz-v2で確立されたものを
そのまま利用します（本プロジェクト独自の移植元は「助か～る」であり、詳細は後述
「移植元「助か～る」について」参照）。

現状のソース（`src/main/java/com/freedom/taskall_v2/...`）はごく初期の骨組み
（Controller1つ、Thymeleafテンプレート1つ）であり、上記モデルの大部分はまだ設計段階で、
実装はこれからです。パッケージ名が `taskall_v2`（アンダースコア区切り）であることに注意
してください。`-` はJavaのパッケージ名として使えないためです（`HELP.md` 参照）。

## 移植元「助か～る」について

`taskall-v2` は、既存システム「助か～る」
（https://www.taskall.co.jp/ankeninfo/index.jsp）を、remainz-v2で確立した
ローコードエンジンを使って再構築するプロジェクトです。remainzの移植と異なり、
「助か～る」の**既存ソースコードは参照できません**。そのため以下の方針で進めます。

- 移植ではなく、実機（上記URL）の画面・挙動を確認しながら、issueに対象画面の機能概要を
  記述して**新規実装**する。元ソースの完全移植は目指さない。
- 画面外観は現代的な一般アプリの見た目に刷新し、レスポンシブ対応を行う。
- 機能は既存機能の一部を引き継ぎつつ、大幅な改修・増強を前提とする。
- issue作成時は、対象画面のURL・実機で確認した機能概要・引き継ぎたい挙動・
  変更/廃止したい挙動を具体的に記述すると、実装の精度が上がる。

### 新規画面issue対応の一般手順

「助か～る」の画面を新規実装する場合の一般的な手順は次の通りです。

1. 実機（https://www.taskall.co.jp/ankeninfo/index.jsp ）で対象画面の挙動・入力項目・
   遷移先を確認し、issueに機能概要としてまとめる。
2. `documents/guideline/4000001_howToUse.md`の「新規ページを追加する場合」の手順に従い、
   `HTML_PARTS`単位の画面部品を設計し、`src/main/resources/templates/parts/`配下に
   Thymeleafテンプレートとして作成する。権限確認は
   `T(com.freedom.taskall_v2.web.util.AuthUtil).hasReadAuth(...)`/`hasEditAuth(...)`を
   ラッパーの`th:if`で呼び出す。パート横断で画面表示項目を参照する必要がある場合は
   `T(com.freedom.taskall_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, itemKey)`
   を使う。
3. 必要なDBデータ(`URI_PATTERN`, `HTML_PAGE`, `HTML_PARTS`, `PARTS_IN_PAGE`, `SCR`,
   `SCR_ELM`, `REQUIRE_APROLE`, `HTML_PARTS_IN_APROLE`)を`src/main/resources/db/data`配下に
   新規に作成・登録し(採番規則・共通カラムの規約は本ファイル後述の「DBマスタデータの採番規則」
   参照)、`DbSchemaSqlGeneratorRealDataTest`を実行してSQLを再生成する。
4. 必要なバックエンドサービス(`ScriptElementService`実装クラス)を新規実装する。例外方針
   (`BusinessRuleViolationException`/`ApplicationInternalException`)・JSON入出力方針
   (`tools.jackson.*`、`MsgUtil`経由のログメッセージ)は本プロジェクトの規約に従う。
5. 各サービス・ユーティリティクラスに、Mockitoベースの対応するテストクラスを追加する
   (`@ExtendWith(MockitoExtension.class)`、DBアクセスは`RecordQueryService`をモック化)。

## パッケージ構成とコントローラの方針（`documents/design/2000001_base_design.md` 参照）

- `taskall-v2` のパッケージは `com.freedom.taskall_v2` 配下に `common`（DB非依存の共通資材:
  `db`, `exception`, `service`, `util`）と `web`（Webアプリ固有資材: `controller`,
  `exception`, `service`, `util`）を配置する。移植元「remainz」にあった `common/param`
  （`GenericParam`）と `web/servlet` は廃止し、`web/controller` を新設する。
- **コントローラは `TaskallV2Controller` 1つのみ**とする。`URI_PATTERN` テーブルの値を
  `@GetMapping`/`@PostMapping` に、`HTML_PAGE` テーブルで `SCR_ID_GET`/`SCR_ID_POST` 等が
  0でないものをコントローラのメソッドとして記述する。各メソッドはDBレコードに基づいて
  処理するサービスを呼び出すだけとし、移植元の
  `com.remainz.web.servlet.ServiceControlServlet#controllService` を参考にする。
- 仮実装の `TaskallV2Controller`（現状 `controller/` パッケージ直下）は上記構成に従って
  いないため、実装を進める際は配置を見直すこと。

## 例外処理の方針（`documents/design/2000001_base_design.md` 参照）

- 業務的なエラーは `BusinessRuleViolationException`、それ以外のシステム的なエラーは
  `ApplicationInternalException` をスローする（いずれも移植元 `com.remainz.common.exception`
  配下のクラスが元）。入力JSONの必須パラメータ欠如は前者、IOException等の技術的失敗は後者。
- 両例外は **RuntimeException派生**とし、`throws` を書かずに済むようにする。
  チェック例外をキャッチしてまで包み直す必要はなく、`throws` が必須な例外のみ
  catchしてrethrowする。グローバル例外ハンドラは
  `BusinessRuleViolationException` / `ApplicationInternalException` / `Exception`（その他全て）
  の3種類に絞る。

## スクリプト処理とパラメータの渡し方（`documents/design/2000001_base_design.md` 参照）

- `SCR_ELM.SERVICE_NAME` にJavaクラスの完全修飾名を格納し、任意の業務ロジックを任意の順で
  呼び出す「スクリプト」の仕組みは踏襲する。
- 移植元では入出力に `GenericParam` クラスを使っていたが、`taskall-v2` では **String型の
  JSON文字列**に置き換え、`ObjectMapper` で必要なパラメータを取得する。ある業務ロジックの
  出力JSONが次の業務ロジックの入力JSONになる点は変わらない。

## 画面パーツ・画面表示項目のJSON構造（issue #9、`documents/design/2000004_model_driven_web_design.md` 参照）

- 移植元「remainz」の `CreateHtmlService` は、`PARTS_ITEM.ITEM_KEY` をトップレベルの
  キーとしてリクエスト属性(≒JSONのトップレベル)に展開し、JSP側では
  `<c:forEach items="${htmlPage}">` で `PARTS_IN_PAGE` 単位のパーツをループしつつ、
  各パーツが使う画面表示項目はトップレベルの `${itemKey}`（例: `${systemName}`,
  `${urlLink}`）に直接アクセスする構成だった。この方式は、同一画面に同じ `HTML_PARTS`
  （＝同じ`ITEM_KEY`の組)を持つパーツが2つ以上あると、後から処理したパーツの結果で
  前のパーツの結果を上書きしてしまう衝突バグを内包していた。
- `taskall-v2` では、`htmlPage` 配列の各要素（`PARTS_IN_PAGE_ID` 単位）に、そのパーツの
  画面表示項目を `items` 配列としてネストする構造に変更し、この衝突を構造的に防止する。
  Thymeleaf側は `th:each="part : ${htmlPage}"` → `th:each="item : ${part.items}"` →
  `th:each="record : ${item.records}"` の3段ループで、`item.itemKey` により表示対象の
  項目を判定してから `record.<カラム物理名>` で値を参照する（`records` 内のフィールド名は
  カラム物理名のまま、キャメルケースに変換しない）。具体例は
  `documents/design/2000004_model_driven_web_design.md` の「出力JSON構造の改善」節、
  実装は `CreateHtmlService`/`10000_contents.html` を参照。
- 今後、移植元のJSPをThymeleafへ移植する際は、上記の理由から旧JSPの
  `${itemKey}` 直接参照をそのまま移植せず、`htmlPage`/`items`のネスト構造に沿った
  ループへ書き換えること。

## JSP→Thymeleaf移植時の画面部品ファイル拡張子（PR #10 レビュー指摘）

- 移植元「remainz」はサーブレット＋JSPだったため、`HTML_PAGE.DESTINATION_～`（forward先）
  やコントローラのビュー名解決処理は `.jsp` を前提としていたが、`taskall-v2` では画面部品の
  ファイルに `.jsp` を使わず、`10000_contents.html` のように**常に `.html` を使う**方針とする。
- そのため、DBデータ（`DESTINATION_GET`等）・コントローラ側のビュー名解決処理
  （`TaskallV2Controller`）ともに、拡張子は `.jsp` ではなく `.html` を前提として実装・移植する。

## 画面デザイン・配色の方針（issue #9「デザインの改善」対応）

- 画面デザインは引き続きBootstrap（`src/main/resources/static/css/bootstrap.min.css`）を使用し、
  レスポンシブデザインを維持する。ただしDBメンテナンス系画面（テーブル定義参照・データ編集・
  レコード参照など）は、PCでの利用を主目的とするため、無理にレスポンシブ対応しなくてよい。
- 配色は個別のHTML要素へ`style`属性や固有クラスでベタ書きせず、
  `src/main/resources/static/css/rwstyle.css`の`:root`ブロックでBootstrap 5.3の
  CSSカスタムプロパティ（`--bs-primary`等）を上書きする方式に統一する。これにより、
  画面全体の色味を変更したい場合は`rwstyle.css`の変数上書きを直すだけで済み、
  各テンプレート側は`btn-primary`/`bg-primary`のような標準クラスのみを使い続ければよい。
- 共通ヘッダ（`20010_commonHeader.html`）は灰色(`bg-secondary`)ではなくテーマカラー
  (`bg-primary`)を使用する。「TOP」「マイページ」等のリンクは`btn-outline-light rounded-pill`
  でタブ風の見た目にし、単なるラベルとリンクが区別しづらい状態を避ける。
- ログイン画面（`20030_commonLogin.html`）のような入力フォームは、`row justify-content-center`
  ＋`col-md-*`で中央寄せし、`card`＋`form-label`/`form-control`を用いた縦積みレイアウトとする。
- マイページ等のボタン一覧（`20040_commonLinkList.html`等）は、`btn w-100`で単純に縦に
  並べるのではなく、`row row-cols-1 row-cols-md-2 g-3`のようなグリッドでカード状に整列させる。
- DBメンテナンス系のtable要素は、`table-responsive`でラップした上で`w-auto`とし、
  `d-flex justify-content-center`で中央寄せする（レスポンシブ対応は不要だが、中央寄せは行う）。

## ログメッセージの管理方針（PR #10 レビュー指摘）

- `ERROR`/`WARN`レベルでログ出力する文字列は、直接記述せず
  `src/main/resources/msg/messages.properties`にメッセージキー（`msg.err.～`/`msg.warn.～`）
  として定義し、`MsgUtil#get`経由で取得する。1箇所でしか使わない文字列であっても
  `messages.properties`に集約する（本番リリース後のトレース対象となるため）。
  同じ文字列を2箇所以上で使う場合だけ定数化すればよいという考え方だと、結局同じような
  メッセージが各所に散らばってしまうための方針。
- `INFO`レベルはこの限りではなく、要所ごとに記録するもので場所がほぼ自明なため、
  引き続き文字列を直接記述してよい。

## コメントの記述方針（PR #10 レビュー指摘）

- 上記の一般的なコメント方針に加えて、5～10行程度の処理のかたまり（処理ブロック）ごとに、
  その処理の概要を説明する**ブロックコメント**を付与する方針とする。
- 空行で区切られた単位が、おおまかな処理の分かれ目の目安となる。ブロックコメントで概略を
  つかんでから詳細なコードを読めるようにすることで、初見の開発者やルーキーにとっても
  保守時の読み解きが速くなることを狙いとする。
- 移植元「remainz」のコードはなるべくブロックコメントが付与されているため、内容が妥当で
  あればそのまま踏襲してよい。ただしコピー＆ペーストのまま実態と合っていないコメントが
  残っていないか確認すること。

## DBアクセスの方針（`documents/design/2000001_base_design.md` 参照）

- 移植元の `com.remainz.common.db.GenericDb` に相当する仕組みは、変更せずそのまま採用する
  （MyBatisやJPAへの移植は複雑化を招くため行わない）。
- SELECT結果は `ArrayList<LinkedHashMap<String, String>>`（1レコード=`LinkedHashMap`、
  カラム順序はSELECT記述順を維持）で返却し、値は元の型（INT/DATETIMEなど）に関わらず
  一律文字列として扱う。画面表示用データは全て文字列という割り切りに基づく。
- `DataSource` をインジェクションし `PreparedStatement`/`ResultSet` を使う実装とする。
  トランザクションはSpringBootに任せ、commitは1箇所に集約する（複数箇所でcommitする
  コードは避ける。移植元では `ServiceControlServlet` の1箇所のみでcommitしていた）。
- SQLiteはh2/MySQLと書き方が異なる可能性があるため、差異が出た場合はSQLite向けの
  パッケージを新設し、そこに専用のSQL生成/アクセスコードを配置する（移植元の
  `DbInterface`/`H2Db`/`MysqlDb` のようにDB種別ごとに実装を切り替えられる構造を踏襲）。

## DB定義・DBデータ資材の生成（`documents/design/2000001_base_design.md` 参照）

- `src/main/java/.../common/db` 配下は `data`（SQL生成元となる各テーブルのデータ資材）と
  `sql`（`data`から生成したSQL）に分けて管理する。
- DB定義は `src/main/resources/db/data/TBL_DEF.txt` で管理する（移植元は
  `src/test/resources/service/script/dbmng/h2/20_dbdata/TBL_DEF.txt`）。不要なテーブルを
  除き、基本的にはそのまま流用する。各テーブルのDROP/CREATE SQLはこのファイルから生成する
  （移植元の `GetTableCreateSqlService`/`GetTableDropSqlService` が該当）。
- DBデータは `src/main/resources/db/data` 配下（1テーブル1ファイル）で管理し、INSERT/SELECT SQL
  を生成する（移植元の `GetTableInsertSqlService`/`GetTableSelectSqlService` が該当、
  移植元データは `src/test/resources/service/script/dbmng/h2/20_dbdata/10_authorized` 配下）。
- DB定義・DBデータいずれについても、生成したSQLは `src/main/resources/db/sql` に配置する
  （生成元の`data`と生成物の`sql`を明確に分離する）。
- これらSQL生成コードを移植する際も、例外処理・入出力（JSON文字列化）は上記の新方針に
  合わせて書き直す。
- `TBL_DEF` テーブルはDB内のテーブル定義自体を保持する特殊テーブルで、`VERSION` 以下の
  定型カラムを持たない。`FOREIGN_TABLE`（外部キー先テーブル）、`DESC_FIELD`
  （サロゲートキーの意味を説明する実質的なキー項目、例: `ACCNT.ACCNT_ID` に対する
  `ACCOUNT_NAME`）などの特殊カラムを持つ。

## 本番DB更新の仕組み（issue #72、Flyway導入）

- `src/main/resources/db/data`/`db/sql` は、あくまで**新規（未初期化）DBを最新スキーマ・
  最新マスタデータの状態でブートストラップするため**の資材であり（`DbInitializer`が
  「TBL_DEF」テーブル不在を検知した初回起動時のみ実行）、既にレコードが存在する
  本番DBへ「後から差分だけを当てる」用途には使えない。本番DBへスキーマ変更・マスタデータ
  追加を反映する場合は、**Flyway**（`org.flywaydb:flyway-core`、バージョンはSpringBootの
  dependency-managementプラグインが管理）を使った差分マイグレーションを用いる。
- マイグレーションファイルは `src/main/resources/db/flyway` 配下に `V2__xxx.sql`,
  `V3__xxx.sql`, ...の形式（Flyway標準の命名規則）で追加する。「V1」は「Flyway導入前の
  状態」を表す暗黙のベースラインとして予約済みのため、実ファイルはV2から作成する。
- マイグレーションSQLの内容は、対象issueのPRにおける `src/main/resources/db/data/` の
  git diffを基に、**新規追加された行のみ**（既存の共有マスタテーブル、例:
  `HTML_PAGE`/`SCR`/`URI_PATTERN`等は本番に既存レコードがあるため、テーブル全体の
  INSERTではなく差分のみ）を抽出して作成する。カラムの数値/文字列判定は
  `TBL_DEF.txt`を参照し、`InsertSqlBuilder`と同じクオート規則（INTは非クオート、
  それ以外は単一引用符で囲み`'`は`''`にエスケープ）に従う。SQLiteはDDLの列制約変更
  （例: 既存カラムへの`UNIQUE`制約追加）に`ALTER TABLE`が使えないため、代わりに
  `CREATE UNIQUE INDEX IF NOT EXISTS`等、SQLite/MySQL双方で意味的に同等な代替構文を使う。
- SpringBootの自動Flyway設定（`FlywayAutoConfiguration`）は`application.yaml`の
  `spring.flyway.enabled: false` で無効化している。本番DB更新は、`DbInitializer`
  （`@Order(1)`、初回ブートストラップ担当）の後に実行される`FlywayMigrationRunner`
  （`@Order(2)`）が、`FlywayMigrationService`経由で独自に`Flyway`インスタンスを構築して
  実行する。`DefaultAccountCredentialInitializer`（デフォルトアカウントのパスワード
  差し替え、issue #41）は`@Order(3)`とし、ACCNTスキーマ変更を含むマイグレーション適用後に
  実行されるようにしている。
- 「TBL_DEF」テーブルが既に存在するDB（Flyway導入前からの既存本番DB等）と、この起動で
  `DbInitializer`が最新スキーマとして新規作成したDB（開発環境の`rm -f taskallv2.db`後の
  起動等）とでは、ベースライン化の方針が異なる（詳細は`FlywayMigrationService`の
  Javadoc参照）。前者は「V1」としてベースライン化した上で未適用のマイグレーションを適用し、
  後者は「db/data」の最新資材を反映済みのため、発見できる最新バージョンとしてベースライン化
  する（マイグレーションの二重適用によるテーブル重複エラーを避けるため）。この判定は
  `DbBootstrapState`（`DbInitializer`が新規作成した場合に`true`を設定する状態保持Bean）を
  介して行う。
- 本番反映時は、当該マイグレーションファイルを追加した資材を本番環境へデプロイし、
  アプリ再起動によって`FlywayMigrationRunner`が自動的に未適用分のみを適用する（手作業での
  SQL実行は不要）。

## DBマスタデータの採番規則（`documents/design/2000001_base_design.md` 参照）

- IDは `1000001` から採番する（`1`〜`1000000` は予約領域として使用しない）。
- 基本は100番ずらし（`1000001`, `1000101`, ...）。ただし「はい/いいえ」のように意味的に
  グルーピングできるレコード群は、100番ずらしではなく連番（`1000301`, `1000302`, ...）で
  採番する。採番方法に迷う場合は必ず質問すること。
- 新規レコードは `CREATED_BY`/`UPDATED_BY` を `data_loader`、`CREATED_AT`/`UPDATED_AT` を
  作成日の `00:00:00`、`VERSION` を `1`、`IS_DELETED` を `0` とする。
- レコード変更時は `VERSION` を1増やし `UPDATED_AT` を更新日の `00:00:00` にする（単純ミス
  修正など、`VERSION` を変えない旨の明示的な指示があればそちらを優先する）。

## アーキテクチャ／スタック

- Java 21、Spring Boot（`spring-boot-starter-webmvc`, `-jdbc`, `-thymeleaf`, `-mail`, `-validation`）。
- DBアクセスは素のJDBC（`spring-boot-starter-jdbc`）であり、JPAやMyBatisではありません。
  `documents/knowledge/` 配下の一部資料はMyBatisに言及していますが、これは関連する別
  プロジェクトの知見が混在しているためで、本プロジェクトの依存関係には含まれません。
- DB: 現状はローカル・本番ともにSQLite（`org.xerial:sqlite-jdbc`、`application.yaml` の
  `jdbc:sqlite:./taskallv2.db`）。将来的に本番はMySQLへ移行予定のため、**SQLite固有の
  SQL構文は使わない**ようにしてください（`documents/design/2000001_base_design.md`）。
- ビュー層: `src/main/resources/templates/` 配下のThymeleafテンプレート。

### DBテーブルの命名規則（`documents/design/2000001_base_design.md` 参照）

- 主キーは必ず自動採番のサロゲートキーとし、物理名は `<テーブル物理名>_ID` とする。
- 外部キーの物理名は `<参照先テーブル物理名>_ID` とし、フィールド名だけで関連が分かるようにする。
- テーブル名・カラム物理名は英大文字＋アンダーバー、28文字以内。文字数を抑えるため略語使用可。
- 全テーブル共通で `VERSION`, `IS_DELETED`, `CREATED_BY`, `CREATED_AT`, `UPDATED_BY`,
  `UPDATED_AT` を付与する。

## ビルド／テスト

- ビルド: `./gradlew build`
- 全テスト実行: `./gradlew test`
- 単一テストクラスの実行: `./gradlew test --tests "com.freedom.taskall_v2.TaskallV2ApplicationTests"`
- 単一テストメソッドの実行: `./gradlew test --tests "com.freedom.taskall_v2.TaskallV2ApplicationTests.contextLoads"`
- JDK 21が必要（`JAVA_HOME` の設定必須）。Windowsでの環境変数設定例（`chcp 65001` や
  `JAVA_OPTS=-Dfile.encoding=UTF-8` など）は `documents/knowledge/build.md` 参照。
- インジェクション対象クラスが見つからない、といったテストエラーの場合は、まず
  `application.yaml` / `application.properties` の設定漏れを疑ってください。使用している
  Spring Bootの機能（datasource, mailなど）に必要な設定項目が無いとコンテキストが起動
  できません（`documents/knowledge/gradleError.md`）。

## テストの方針

- 単体テストは**Mockito**を使用し、DBを使う結合テストは基本的に行いません。A -> B -> C -> D
  のような多段の呼び出しがあっても、直下の子（例: A -> Bのテストなら B）だけをモックすること
  で、階層ごとに分割してテストできます（`documents/knowledge/junit.md`）。パターン:
  テストクラスに `@ExtendWith(MockitoExtension.class)`、協働クラスに `@Mock`、
  テスト対象クラスに `@InjectMocks` を付与し、`when(...).thenReturn(...)` でモックの
  挙動を事前設定してから、実際のメソッドの挙動をassertで検証する。
- 実装クラスには必ずペアとなるテストクラスを用意すること。テストの無い実装はNGとされます
  （`documents/design/2000003_implementation_design.md`）。

## 開発の進め方（AI駆動開発フロー）

本プロジェクトはissue -> ブランチ -> pull requestのループで、AIエージェントによって開発が
進められます（`documents/design/2000003_implementation_design.md`）。

- **作業着手前に、必ず`documents/rules/`配下の全ファイルを読むこと。** 環境変数の設定先
  （JAVA_HOME、アプリ起動ポート等）、ブランチ作成・プルリク作成の具体的なコマンド、
  DBクリーンアップ手順など、開発効率化のためAIとの過去のやり取りを踏まえて策定された
  規則が記載されている。既知の内容であっても、探索や試行錯誤を省略しクレジット消費を
  抑えるため、都度確認してから作業に着手すること。
- **issueの指定が無い実装依頼には着手しないこと。** issueのURLが示されていない場合は
  「実装依頼にはissueが必要です」と回答し、実装作業を行わない。
- 実装作業を開始する前に、**superpowers**（https://github.com/obra/superpowers）が
  有効かどうかを確認すること。`using-superpowers`, `brainstorming`,
  `test-driven-development`, `systematic-debugging`, `writing-plans` などsuperpowers
  プラグイン由来のスキル群が利用可能なスキルとして読み込まれているかをチェックする。
- ブランチ名は `feature/<issue番号>`（例: issue #1 なら `feature/1`）とする。
- 実装には必ず対応するテストを付けること（上記参照）。
- pull requestは `feature/<issue番号>` から `develop` ブランチ宛てに作成する（`main` ではない）。
- 実装完了時はpull requestのURLを成果物として報告する。
- issueの記載内容に不明点がある、論理的に矛盾している、といった場合は**自律的に仕様を
  訂正・補完せず、必ず質問すること。**
- pull requestへの指摘対応時も、該当のコメント内容を確認し、実装・テスト双方を修正する。
  この場合も不明点があれば必ず質問すること。
- **issue対応が完了した際は、`documents/rules/1000002_issue_points.md`に、対応した
  issueのポイント（issue URL、PR URL、関連する相対パス、決定事項・規約の要点）を
  `###`見出しのセクションとして追記すること。** これにより、今後の作業で当該issueや
  pull requestの全文を読み返さなくても、要点だけを短時間で把握できるようにする
  （`documents/rules/1000002_issue_points.md`の`## 概要`節も参照）。
- **トラブル対応の履歴を、`documents/rules/1000003_trouble_points.md`に記録する。
  対応したトラブルの概要、原因、対応、防御策を、`###`見出しのセクションとして追記すること。**
  これにより、今後の作業で注意すべきトラブルの防御策を把握できるようにする
  （`documents/rules/1000003_trouble_points.md`の`## 概要`節も参照）。

## ドキュメントの管理方法

- `documents/design/` — 論理設計・アーキテクチャ設計資料（`2000xxx_*.md` の連番）。
- `documents/knowledge/` — Gradle、JUnit、ログ、バッチ、MinIO、Redisセッションなど、
  個別技術トピックに関するナレッジ資料。`README.md` からリンクされている。
- `documents/prompts/` — AIに対して行った問い合わせ内容（プロンプト）の保存先。
- `documents/rules/` — AIの回答をルールとして保存する場所。一部ファイルは
  `[AI回答のマークダウンを貼り付けて保存する]` のままの未着手プレースホルダーであり、
  内容が未整備の状態のものがある。
- ルール類の陳腐化防止のため、定期的に `documents/rules/` の内容を見直し、それに応じて
  コード・テストを更新する運用とする。
- `documents/superpowers/` — superpowersプラグインの実装計画等、superpowers関連の資材の
  保存先。他の`documents/`配下ディレクトリと同様の置き場所に統一するため、`docs/`配下には
  配置しない。
