# 基本設計

---

[READMEに戻る](../../README.md)

---

## 全体方針について

- 分かりやすい、最小限のプロジェクト構成を目指します。
    - 巨大な共通処理に束縛されて、身動きが取れなくなる構成を避けることを、常に意識します。
    - ネット検索して一番最初に出てくるサンプルのような、極力簡明なコードで構成することを目指します。
- 当該プロジェクトでは実務を助けるローコードツールを目指します。
    - 画面構成や呼び出す業務ロジックの情報をDBレコードとする論理設計を行います。
    - 開発言語やアーキテクチャが変わっても同じ機能を実現できるよう、論理設計を進めます。
- 過去の事例で「こういうことができたらな」と思う事項があれば、積極的に機能として盛り込みます。
    - 「実務を助けるローコードツール」という観点を大事にします。
    - 使う人にとって設定や使用方法が簡単で、役に立つツールを目指します。
- 当該プロジェクトではthymeleaf＋SpringBootでプログラムを構成します。
    - 「どんな言語でも実装できるよう論理設計する」だけでは、具体性がなく曖昧です。
    - アーキテクチャを明示し、設計から実装までの道筋を立てやすくします。

---

## remainzについて

- 「remainz」とはWebアプリ構築が可能なローコードツールの名称です。
    - 当該プロジェクトの元プロジェクトとなっています。
    - 「[remainz](https://github.com/freedomRemains/remainz)」及び「[remainz-v2](https://github.com/freedomRemains/remainz-v2)」という2つの元プロジェクトが存在します。
    - 「v2」はバージョン2シリーズであることを意味します。AIに全ての実装を任せているのは「remainz-v2」からです。
- Webアプリに必要な構成要素をDBレコードとして用意します。

---

## DB

- ローカル環境、本番環境(AWS)ともに、SQLiteを使用します。
- 最終的に本番環境ではMySQLを使用しますが、本格運用が決まってからとします。
- それまではSQLite固有の文法を使わないようにし、スムーズな移行ができる状態を維持します。

---

## 開発言語

- Java(SpringBoot)を使用します。

---

## フレームワーク

- thymeleaf＋SpringMVCを使用します。

---

## DBテーブルについて

DBテーブルには、次の規則を設けます。

- 主キーは、必ず自動採番のサロゲートキーとします。
- 主キーフィールドの物理名は、必ず「テーブル物理名 + "_ID"」とします。
- 外部キーフィールドの物理名は、必ず「テーブル物理名 + "_ID"」とします。

これはフィールド名だけで、テーブル間の関連が分かるようにするための規則です。  
次の例では汎用コードマスタが、外部キーとして汎用グループマスタのIDを持っていることが分かります。

| テーブル物理名       | テーブル論理名       |
| -------------------- | -------------------- |
| GNR_GRP              | 汎用グループマスタ   |

| フィールド物理名     | 型                   | フィールド論理名     |
| -------------------- | -------------------- | -------------------- |
| GNR_GRP_ID           | INT                  | 汎用グループID       |
| GNR_GRP_NAME         | VARCHAR(256)         | 汎用グループ名       |
| ...                  |                      |                      |

| テーブル物理名       | テーブル論理名       |
| -------------------- | -------------------- |
| GNR_KEY_VAL          | 汎用コードマスタ     |

| フィールド物理名     | 型                   | フィールド論理名     |
| -------------------- | -------------------- | -------------------- |
| GNR_KEY_VAL_ID       | INT                  | 汎用コードマスタID   |
| GNR_KEY              | VARCHAR(256)         | キー                 |
| GNR_VAL              | VARCHAR(256)         | 値                   |
| GNR_GRP_ID           | INT                  | 汎用グループマスタID |
| ORD_IN_GRP           | INT                  | グループ内順序       |
| ...                  |                      |                      |

---

## DB定義の記述規則について

- テーブル名及びカラム名の規則
    - 物理名は28文字以内とします。
    - 物理名は英大文字をアンダーバーで連結した文字とします。
    - 物理名を短くするため、適宜、略語の使用を許可します。
- 次に挙げる各項は、基本的に全てのテーブルに定義してください。

| フィールド物理名     | 型                   | フィールド論理名     |
| -------------------- | -------------------- | -------------------- |
| VERSION              | INT                  | バージョン           |
| IS_DELETED           | INT                  | 削除フラグ           |
| CREATED_BY           | VARCHAR(128)         | 作成者               |
| CREATED_AT           | TIMESTAMP            | 作成日時             |
| UPDATED_BY           | VARCHAR(128)         | 更新者               |
| UPDATED_AT           | TIMESTAMP            | 更新日時             |

---

## パッケージ構成について

- 移植元である「remainz」のパッケージ構成は、次の通りです。

```
src/main/java/com/remainz
	common              Webやバッチなどの仕組みに依存しない、共通的な資材を配置する
		db                  DBに関連する資材を配置する
			data                SQL生成の元資材となる各テーブルのデータを配置する
			sql                 dataディレクトリに基づき作成したSQLを配置する
		exception           共通例外の資材を配置する
		param               共通パラメータの資材を配置する(taskall-v2では廃止)
		service             共通業務ロジック(サービス)の資材を配置する
		util                共通ユーティリティの資材を配置する
	web                 Webアプリの資材を配置する
		exception           Webアプリに固有の例外資材を配置する
		service             Webアプリに固有の業務ロジック(サービス)を配置する
		servlet             Webアプリのサーブレット資材を配置する(taskall-v2では廃止)
		util                Webアプリに固有のユーティリティ資材を配置する
```

- 移植先である「taskall-v2」のパッケージ構成は、次の通りとします。

```
src/main/java/com/freedom/taskall_v2
	common              Webやバッチなどの仕組みに依存しない、共通的な資材を配置する
	    db                  DBに関連する資材を配置する
	    exception           共通例外の資材を配置する
	    service             共通業務ロジック(サービス)の資材を配置する
	    util                共通ユーティリティの資材を配置する
	web                 Webアプリの資材を配置する
		controller          Webアプリのコントローラ資材を配置する
		exception           Webアプリに固有の例外資材を配置する
		service             Webアプリに固有の業務ロジック(サービス)を配置する
		util                Webアプリに固有のユーティリティ資材を配置する
```

- なおコントローラは「TaskallV2Controller」のみとします。
    - 「URI_PATTERN」テーブルの「URI_PATTERN」フィールドの値で、@GetMappingや@PostMappingを記述します。
    - 「HTML_PAGE」テーブルの「SCR_ID_GET」や「SCR_ID_POST」が0でないものは、コントローラに記載が必要です。
- 「TaskallV2Controller」ではDBレコードに基づき、業務ロジック(サービス)を実行するスクリプトを呼び出すのみとします。
    - 移植元「remainz」配下の「src/main/java/com/remainz/web/servlet/ServiceControlServlet.java」にある「controllService」が参考になります。
    - 移植先の「taskall-v2」では「handleRequest」という処理名に変わっています。以下、実装コード例です。

```Controller
    @GetMapping("/taskall-v2/service/myPage.html")
    public String getMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/myPage.html")
    public String postMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }
(以下、略)
```

---

## 例外処理について

- 移植元となっている「remainz」の例外処理が中途半端で使いづらかったため、次の方針に変更します。
    - 業務的なエラーは「BusinessRuleViolationException」をスローします。
    - それ以外のシステム的なエラーは「ApplicationInternalException」をスローします。
    - 「BusinessRuleViolationException」「ApplicationInternalException」ともに、RuntimeException派生です。
    - 入力JSONに所定のパラメータがない、といったものは前者(業務的なエラー)に該当します。
    - 入力JSONにあるファイルを開いたらIOExceptionが起きた、といった場合は後者(システム的なエラー)に該当します。
    - 移植元の「remainz」では例外を読み替えないことに重きを置いてExceptionをそのままスローしていました。
    - これをRuntimeExceptionスローとすることでコードをすっきりさせ、毎回throwsステートメントを書かないようにします。
    - なおRuntimeExceptionをキャッチしてApplicationInternalExceptionで出しなおす必要はありません。
    - throwsを記述しなければならない例外のみ、キャッチしてスローし直しをお願いします。
    - こうすることでtry-catchを最小限の範囲におさえ、グローバル例外ハンドラは「BusinessRuleViolationException」「ApplicationInternalException」「Exception(その他全例外)」のみとできます。

---

## スクリプト処理とパラメータの渡し方について

- 移植元となっている「remainz」ではDBの「SCR_ELM」というテーブルの「SERVICE_NAME」にJavaクラスの完全修飾名を格納していました。
- スクリプト形式で任意の業務ロジックを、任意の順番で呼び出すための仕組みです。
- 各業務ロジッククラスは「src/main/java/com/remainz/common/param/GenericParam.java」というパラメータを入力／出力に使っていました。
- ある業務ロジックの出力パラメータは、次に呼び出す業務ロジックの入力パラメータになります。
- SpringBootへの移植に当たっては、この「GenericParam」をString型のJSON文字列に変更し、ObjectMapperで必要なパラメータを取得するよう変更します。
- 移植元「remainz」の「～Service」クラスを参照すると分かりますが、最初に入力パラメータに必須項目があるかチェックしています。
- この箇所をStringのJSONからパラメータを取得するコードに変更します。(ObjectMapperを使います)

---

## DBクエリについて

- 移植元となっている「remainz」では、DBクエリを「src/main/java/com/remainz/common/db/GenericDb.java」という共通クラスで行っています。
- クエリ(SELECT)結果をArrayList<LinkedHashMap<String, String>>という型にして返却しています。
    - LinkedHashMap<String, String>はテーブルの1レコードを指します。
    - SELECTに記述した順序通りになるよう、LinkedHashMapを使っています。
    - これを更にArrayListで囲み、複数行の検索結果をORDER BYの順番を変えることなく返却します。
- DBの元の型がINTやDATETIMEであっても、一律文字列で返却することになります。
- 画面表示データを全て文字列として解釈する、という割り切りをしています。
    - 型を細かく意識しだすと、ローコードツールの構造が複雑になってしまうためです。
    - 画面返却JSONに「TBL_DEF」のクエリ結果を入れれば、元となっているデータの型も一応判別可能です。
    - 実際に「TBL_DEF」のクエリ結果を返しているコードもあるので、この割り切りでも十分であることは確認済みです。
- スクリプトで実行される複数の業務ロジックで、画面にHTMLで表示したい内容を作成します。
- この仕組みは移植対象コードの中核をなすため、変更せずそのままこの仕組みを採用するものとします。
    - MyBatisやJPAへの移植は複雑化が懸念されるため、行いません。
    - 「DataSource」をインジェクションしてPreparedStatementを使い、ResultSetを得るコードは実装可能です。
    - トランザクションはSpringBootに任せる形が望ましいです。
    - dataSource.getConnectionでもよいですが、複数箇所でコミットできるコードを組むのは望ましくありません。
    - 移植元ではcommitは「src/main/java/com/remainz/web/servlet/ServiceControlServlet.java」の1箇所だけです。
    - 実装で気を付けて制御しているだけであり、仕組みとしてcommit/rollbackを隠ぺいできれば、そちらの方がよいです。
- 画面表示のためのJSONイメージは、次の通りです。(実際にはキーをもう少し細かく設定しますので、実物通りではありません)

```JSON
html: {
	account: [
		{ACCNT_ID: 1000001, ACCOUNT_NAME: ゲスト, MAIL_ADDRESS: guest@account.com, VERSION: 1, IS_DELETED: 0, CREATED_BY: data_loader, CREATED_AT: 2021-07-12 00:00:00, UPDATED_BY: data_loader, UPDATED_AT: 2021-07-12 00:00:00},
	],
	authList: [
		{HTML_PARTS_ID: 1000001, AUTH_KIND: read},
		{HTML_PARTS_ID: 1000101, AUTH_KIND: read},
		{HTML_PARTS_ID: 1000201, AUTH_KIND: edit},
		{HTML_PARTS_ID: 1000701, AUTH_KIND: read},
		{HTML_PARTS_ID: 1001101, AUTH_KIND: read},
	],
	htmlPage: [
		{PARTS_IN_PAGE_ID: 1000001, HTML_PAGE_ID: 1000001, PAGE_NAME: TOP, RESP_KIND_GET: forward, RESP_KIND_POST: forward, RESP_KIND_PUT: redirect, RESP_KIND_DELETE: redirect, DESTINATION_GET: 10000_contents.jsp, DESTINATION_POST: 10000_contents.jsp, DESTINATION_PUT: top.html, DESTINATION_DELETE: top.html, HTML_PARTS_ID: 1000001, PARTS_NAME: システム名, PARTS_ITEM_ID: 1000001, ITEM_KEY: systemName, ITEM_QUERY: SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'},
		{PARTS_IN_PAGE_ID: 1000002, HTML_PAGE_ID: 1000001, PAGE_NAME: TOP, RESP_KIND_GET: forward, RESP_KIND_POST: forward, RESP_KIND_PUT: redirect, RESP_KIND_DELETE: redirect, DESTINATION_GET: 10000_contents.jsp, DESTINATION_POST: 10000_contents.jsp, DESTINATION_PUT: top.html, DESTINATION_DELETE: top.html, HTML_PARTS_ID: 1000002, PARTS_NAME: 共通ヘッダ, PARTS_ITEM_ID: 1000002, ITEM_KEY: urlLink, ITEM_QUERY: SELECT A.PAGE_NAME, B.URI_PATTERN FROM HTML_PAGE A LEFT JOIN URI_PATTERN B ON A.URI_PATTERN_ID = B.URI_PATTERN_ID WHERE A.SCR_ID_GET = 1100001 OR SCR_ID_GET = 1100201},
	],
	systemName: [
		{GNR_VAL: Remainz},
	],
	urlLink: [
		{PAGE_NAME: TOP, URI_PATTERN: /remainz/service/top.html},
		{PAGE_NAME: マイページ, URI_PATTERN: /remainz/service/myPage.html},
	],
(以下、略)
}
```

---

## DB資材(DROP / CREATE / INSERT / SELECTのSQL)の生成について

- DB定義は「taskall-v2/src/main/resources/db/data/TBL_DEF.txt」で管理します。
    - 移植元では「remainz/src/test/resources/service/script/dbmng/h2/20_dbdata/TBL_DEF.txt」が該当します。
    - 不要なテーブルもあるため、全て一律で移植はしませんが、基本的に資材はそのまま流用します。
    - 各テーブルの DROP / CREATE のSQLは、このファイルに基づいて作成します。
    - 移植元では「TBL_DEF.txt」から「remainz/src/test/resources/service/script/dbmng/h2/10_dbdef/10_authorized」の資材を作っていましたが、余計な処理なのでやめます。
    - SQL生成のソースコードは「src/main/java/com/remainz/common/service/dbmng/common/GetTableCreateSqlService.java」  
      「src/main/java/com/remainz/common/service/dbmng/common/GetTableDropSqlService.java」が該当します。
    - SQL生成のソースコードはいかんせん書き方が古風で洗練されておらず、できれば書き直したいです。
        - 例外処理は見直しを行いたいです。業務的なエラーは「src/main/java/com/remainz/common/exception/BusinessRuleViolationException.java」をスロー、  
          システム的なエラーは「src/main/java/com/remainz/common/exception/ApplicationInternalException.java」をスローしたいです。
        - いずれもRuntimeException派生であり、throwsが不要なものです。
        - 入出力はJSON文字列としたいです。(String)
        - 移植元はGenericParamというものを使っていました。

- DBデータは「taskall-v2/src/main/resources/db/data」配下に資材を配置して管理します。
    - 移植元では「remainz/src/test/resources/service/script/dbmng/h2/20_dbdata/10_authorized」配下の資材が該当します。
    - 不要なテーブルもあるため、全て一律で移植はしませんが、基本的に資材はそのまま流用します。
    - 各テーブルの INSERT / SELECT のSQLは、このディレクトリにあるファイルに基づいて作成します。
    - 1テーブル1ファイルのテキストデータとなっています。
    - SQL生成のソースコードは「src/main/java/com/remainz/common/service/dbmng/common/GetTableInsertSqlService.java」  
      「src/main/java/com/remainz/common/service/dbmng/common/GetTableSelectSqlService.java」が該当します。

- DB定義、DBデータともに生成したSQLは「taskall-v2/src/main/resources/db/sql」に配置するものとします。

- 移植元の「remainz」ではh2及びMySQLをサポートしており、SQL自動生成は両方で実施していました。
    - 「src/test/resources/service/script/dbmng/h2」は、h2向け資材の出力ディレクトリです。
    - 「src/test/resources/service/script/dbmng/mysql」は、MySQL向け資材の出力ディレクトリです。
    - たまたま同じSQLで動かせたため、SQL生成は「src/main/java/com/remainz/common/service/dbmng/common」配下の資材を共通で使っていました。
    - DBアクセスクラスも同様の考え方です。
        - 「src/main/java/com/remainz/common/db/GenericDb.java」で汎用的なDBアクセスクラスを作っています。
        - ただしDBごとにアクセスクラスをカスタマイズできるよう「src/main/java/com/remainz/common/db/DbInterface.java」を設けています。
        - h2向けに「src/main/java/com/remainz/common/db/H2Db.java」、MySQL向けに「src/main/java/com/remainz/common/db/MysqlDb.java」を作っています。
        - いずれも中身が空ですが、DBによってアクセスクラスのコードを変える必要がある場合は、個別に対応できるようにしています。
    - 今回、SQLiteを使用することを想定しています。
    - SQLiteはh2やMySQLと異なる書き方が必要なケースが想定されます。
    - その場合はSQLite向けのパッケージを作成し、そこに独自のSQL生成コードを格納する、といった対応をお願いします。

---

## DBマスタデータの記述規則

- 移植元の「remainz」に準じ、DBマスタデータのID採番は次の規則とします。
    - 「1000001」から始める。(1～1000000までの100万レコードは予約済みID領域として使用しないこととします)
    - 基本的に100番ずらしで「1000001」「1000101」で採番する。
    - ただし所定画面の「はい」「いいえ」のようにグルーピングできるものは100番ずらしではなく連番で採番する。
    - 例えば所定画面の「はい」「いいえ」のリンクのような場合、「1000301」「1000302」、「1000401」「1000402」のように100番ずらしではなく、連番で採番する。
    - 100番ずらしはマスタデータのレコードを間に追加しやすいよう、隙間を設けるための措置。
    - 連番は意味的に連続していることを示すための措置。ID値が大きくなりすぎるのを防ぐ意味もある。
    - 採番の判断に迷う場合は、設計時に必ず質問してください。
    - 新規レコードの場合は「CREATED_BY」と「UPDATED_BY」はいずれも「data_loader」、「CREATED_AT」と「UPDATED_AT」はレコード作成日の「00:00:00」とする。
    - 新規レコードの場合、「VERSION」は常に「1」、「IS_DELETED」は常に「0」とする。
    - レコードを変更した場合は「VERSION」を1増やし、「UPDATED_AT」をレコード更新日の「00:00:00」とする。
        - ただし単純ミス修正などが考えられるため、「VERSIONを1のまま」といった指定があれば、そちらを優先して変更しない。
    - 100番ずらしと連番の採番について、LNK(リンク)テーブルのレコードを例にとると、次のようになる。

```
1000301	はい	1000701	1	1000301	1	1	0	data_loader	2024-09-15 00:00:00	data_loader	2024-09-15 00:00:00
1000302	いいえ	1000301	0	1000301	2	1	0	data_loader	2024-09-15 00:00:00	data_loader	2024-09-15 00:00:00
1000401	はい	1001001	1	1000401	1	1	0	data_loader	2024-09-15 00:00:00	data_loader	2024-09-15 00:00:00
1000402	いいえ	1000301	0	1000401	2	1	0	data_loader	2024-09-15 00:00:00	data_loader	2024-09-15 00:00:00
1000501	確定(新規レコード追加)	1001201	1	1000501	1	1	0	data_loader	2024-09-16 00:00:00	data_loader	2024-09-16 00:00:00
1000502	レコード一覧に戻る	1001501	0	1000501	2	1	0	data_loader	2024-09-16 00:00:00	data_loader	2024-09-16 00:00:00
1000601	確定(レコード削除)	1001301	1	1000601	1	1	0	data_loader	2024-09-16 00:00:00	data_loader	2024-09-16 00:00:00
1000602	レコード一覧に戻る	1001501	0	1000601	2	1	0	data_loader	2024-09-16 00:00:00	data_loader	2024-09-16 00:00:00
```

---

## テーブル定義(TBL_DEF)マスタについて

- 「TBL_DEF」テーブルはDB内のテーブル定義を保持する特殊なテーブルです。
- 次のデータ構造を持っています。

| テーブル物理名       | テーブル論理名       |
| -------------------- | -------------------- |
| TBL_DEF              | テーブル定義         |

| フィールド物理名     | 型                   | フィールド論理名     |
| -------------------- | -------------------- | -------------------- |
| TBL_DEF_ID           | INT                  | テーブル定義マスタID |
| TABLE_NAME           | VARCHAR(256)         | テーブル名           |
| FIELD_NAME           | VARCHAR(256)         | フィールド名         |
| TYPE_NAME            | VARCHAR(256)         | 型                   |
| ALLOW_NULL           | VARCHAR(256)         | NULL許可             |
| KEY_DIV              | VARCHAR(256)         | キー区分             |
| DEFAULT_VALUE        | VARCHAR(256)         | デフォルト値         |
| EXTRA                | VARCHAR(256)         | 拡張                 |
| TABLE_LOGICAL_NAME   | VARCHAR(256)         | テーブル論理名       |
| FIELD_LOGICAL_NAME   | VARCHAR(256)         | フィールド名論理名   |
| FOREIGN_TABLE        | VARCHAR(256)         | 外部テーブル         |
| DESC_FIELD           | VARCHAR(256)         | 説明フィールド       |

- このテーブルのみ、VERSION以下の定型カラムは存在しません。
- 「kEY_DIV」はプライマリキーのみ「PRI」を入力しています。
- 「EXTRA」はサロゲートキーのカラムのみ、自動採番させたいので「AUTO_INCREMENT」を入力しています。
- 「FOREIGN_TABLE」は外部キーの項目である場合のみ、外部テーブルの物理名を入力しています。
    - 例えば「テーブル論理名」が「HTMLページマスタ」、「フィールド名論理名」が「URIパターンID」の場合、「FOREIGN_TABLE」には「URI_PATTERN」を入力しています。
- 「DESC_FIELD」はあるフィールドの説明となる別のフィールドがある場合のみ、そのフィールドの物理名を入力しています。
    - 例えば「アカウント(ACCNT)」テーブルの「アカウントID(ACCNT_ID)」が実際に指し示すのは「アカウント名(ACCOUNT_NAME)」です。
    - 各テーブルのID項目は必ずサロゲートキー(代理キー)ですので、必ずIDに対応する真のキー項目があります。
    - このような場合「アカウント(ACCNT)」テーブルの「アカウントID(ACCNT_ID)」の「DESC_FIELD」は「ACCOUNT_NAME」が入ります。
    - 画面上、意味が分からないアカウントIDの表示を省略する代わりに、アカウント名がリンクになっており、そこにアカウントIDが埋め込まれる、といった使い方ができます。
    - そのため、代理キーを説明できる項目を「DESC_FIELD」に入力しています。

---

[READMEに戻る](../../README.md)

---
