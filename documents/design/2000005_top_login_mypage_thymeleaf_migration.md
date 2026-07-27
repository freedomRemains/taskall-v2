# TOP/ログイン/マイページ Thymeleaf移植設計(issue #11)

## 目的

移植元「remainz」のJSP実装(TOPページ・ログイン・マイページ)を、issue #9で確立した
モデル駆動の仕組み(`URI_PATTERN`/`HTML_PAGE`/`SCR`/`SCR_ELM`/`PARTS_IN_PAGE`/`HTML_PARTS`/
`PARTS_ITEM`)の上で、Spring Boot + Thymeleafへ移植する。対象issueは
[#11](https://github.com/freedomRemains/taskall-v2/issues/11)。

## スコープ

- マイページ(`GET/POST /taskall-v2/service/myPage.html`)の表示・ログイン処理。
- 移植元JSPの `10xxx`(権限確認ラッパー)/`common/20xxx`(画面パーツ本体)構成のうち、本issueで
  必要な4パーツ(ヘッダ、ログインフォーム、リンク一覧、エラーメッセージ一覧)。
- **スコープ外**: Spring Securityによる認証基盤への置き換え(現状のDBベース平文パスワード比較を
  そのまま踏襲し、ハッシュ化は別issueで対応)。

## DBデータ移行

- 必要なマスタデータ(`ACCNT`、`LNK`/`LNK_GRP`、`SCR`/`SCR_ELM`、`REQUIRE_APROLE`、
  `HTML_PARTS_IN_APROLE`、`GNR_KEY_VAL`のログインエラーメッセージ等)はissue #9で移行済みのため、
  新規のマスタデータ投入は不要。
- `SCR_ELM.txt` の `SCR_ELM_ID = 1100201, 1100202, 1100251, 1100252, 1100253` について、
  `SERVICE_NAME` を `com.remainz.web.service.web.*` から `com.freedom.taskall_v2.web.service.*`
  へ変更する。各レコードは `VERSION` を1増やし、`UPDATED_AT` を作業日の `00:00:00` に更新する。
  変更後、`DbSchemaSqlGeneratorRealDataTest` を実行し `INSERT_SCR_ELM.sql` を再生成する。
- `ERR_MSG` テーブルは `TBL_DEF.txt` に定義済みだが、実行時に動的挿入されるテーブルのため
  初期データファイルは不要。

## バックエンドサービス

### LoginService(新設: `web/service`)

- 移植元 `com.remainz.web.service.web.LoginService` を移植する。`ScriptElementService` を実装する。
- `MAIL_ADDRESS`/`PASSWORD` で `ACCNT` を照合する(パスワードは平文比較のまま。ハッシュ化は
  スコープ外)。
- **認証失敗時は例外をスローしない**。移植元と同じPRG(Post/Redirect/Get)パターンを踏襲し、
  内部でcatchして `ErrMsgService` からエラーメッセージキーを取得し、
  `respKind=redirect`, `destination=myPage.html?errMsgKey=<key>` を出力JSONに設定して正常終了する。
  これは通常の例外方針(`BusinessRuleViolationException`/`ApplicationInternalException`)の例外
  ケースであり、UI遷移制御のための意図的な設計である。
- 認証成功時は `context` の `accountId` を認証済みアカウントIDへ更新する。

### ErrMsgService(新設: `web/service`)

- 移植元 `com.remainz.web.util.ErrMsgUtil` に相当する。DB読み書き(`GNR_KEY_VAL`参照、`ERR_MSG`書込)
  を伴うため、静的utilではなく `RecordQueryService` を注入した `@Service` クラスとして実装する。
- エラーメッセージキー発行メソッド(`getErrMsgKey`相当)を提供する。

### AuthUtil(新設: `web/util`)

- 移植元 `com.remainz.web.util.AuthUtil` のうち、DBアクセスを伴わない `hasReadAuth`/`hasEditAuth`/
  `hasAuth` のみを静的メソッドとして移植する(DB取得系メソッドは既に `GetAccountService` が
  カバー済みのため対象外)。

### CreateHtmlServiceの修正

- `errMsgKey` がコンテキストに存在しない場合、パーツ表示項目のSQL実行前に空文字ではなく `"0"` を
  デフォルト設定する(移植元 `CreateHtmlService` の挙動に合わせる)。未設定のままだと
  `errMsgList` 用 `PARTS_ITEM.ITEM_QUERY` 内の `#{errMsgKey}` プレースホルダーが未解決のまま残り、
  SQL構文エラーとなるため。
- **`respKind`/`destination` の上書き防止ガードを追加する**。現状の実装は無条件に
  `output.put("respKind", ...)`/`output.put("destination", ...)` しており、`ScriptExecutionService`
  が各サービスの出力を `context.setAll(...)` で無条件マージする構造と組み合わさると、
  `LoginService` がログイン失敗時にセットした `respKind=redirect`/`destination=myPage.html?errMsgKey=X`
  を、後続の `CreateHtmlService` が上書きして消してしまう。これは移植元の
  `output.putStringIfNotExists(...)`(既存値があれば上書きしない)に相当する挙動を移植できていない
  ためであり、本issueで修正する。対処として、`context` に既に `respKind`/`destination` が存在する
  場合はそれらの値を優先し、`CreateHtmlService` 側では上書きしないようにする。

### TaskallV2Controller

- `myPage.html` 向けの `@GetMapping`/`@PostMapping` を追加する(既存の単一コントローラ方針を踏襲)。

## フロントエンド(Thymeleaf)

### ディレクトリ構成

```
src/main/resources/templates/
  10000_contents.html          … 既存。htmlPage配列をループし、各10xxxフラグメントを呼び出す
  parts/
    10010_header.html          … 権限確認ラッパー(fragment)
    10030_login.html
    10040_linkList.html
    10120_errMsgList.html
    common/
      20010_commonHeader.html  … パーツ本体(fragment)
      20030_commonLogin.html
      20040_commonLinkList.html
      20120_commonErrMsgList.html

src/main/resources/static/
  css/bootstrap.min.css, rwstyle.css
  js/bootstrap.bundle.min.js, rwscript.js
  img/favicon.png
```

移植元の「`10xxx`=権限確認、`20xxx`=本体」という2段構成を、Thymeleafの`th:fragment`を用いた
2ファイル構成でそのまま踏襲する。1ファイルへ統合する方が簡潔だが、issue #11で明示的にこの構成の
移植が依頼されているため、あえて分割を維持する。

各`10xxx`フラグメントは、対象の`HTML_PARTS_ID`一致と`AuthUtil.hasReadAuth`/`hasEditAuth`判定を
`th:if`で行い、条件を満たす場合のみ`th:replace`で対応する`20xxx`本体フラグメントを描画する。
`AuthUtil`の呼び出しは、SpringELの静的メソッド構文
`T(com.freedom.taskall_v2.web.util.AuthUtil).hasReadAuth('1000001', authList)` を用いる。これは
移植元JSPのスクリプトレット呼び出し(`AuthUtil.hasReadAuth("1000001", authList)`)とほぼ1:1で
対応し、レビュー時の突き合わせが容易になる。

`10000_contents.html`は、移植元の静的include羅列と同じ発想で、`th:each="part : ${htmlPage}"`の
ループ内で、本issueが対象とする4パーツ分の`10xxx`フラグメントを`th:replace`で明示的に列挙する
(動的なフラグメント名計算は行わない。移植の透明性・レビュー容易性を優先する)。

### 既知の移植元の仕様(そのまま踏襲するもの)

- HTML_PARTS `1000002`(「共通ヘッダ」)は `PARTS_IN_PAGE` にレコードが存在するが、対応する
  `10xxx`ラッパーが存在せず実質未使用(移植元の名残と推測される)。本issueではこの状態を
  修正せず、そのまま(=対応する`10xxx`フラグメントを作成しない)踏襲する。
- ヘッダの実描画(システム名・URLリンク・アカウント名表示)は、`HTML_PARTS_ID=1000001`
  (システム名)を契機に`10010_header.html`/`20010_commonHeader.html`が丸ごと担う。

### HtmlPageItemUtil(新設: `web/util`)

新しい`htmlPage[].items[]`構造では、画面表示項目は「それが属する`PARTS_IN_PAGE`(=`10xxx`ラッパー)」
単位でネストされる。ところが`urlLink`のデータは`HTML_PARTS_ID=1000002`(共通ヘッダ、無効な
ラッパー)側にネストされているのに対し、実際にナビゲーションリンクを描画するのは
`10010_header`(`HTML_PARTS_ID=1000001`)側であるため、ヘッダーフラグメントは自分がループ中の
partの`items`だけでは`urlLink`に到達できない。この問題に対処するため、`htmlPage`配列全体を
横断して指定した`itemKey`のレコードを検索する静的ユーティリティ`HtmlPageItemUtil.findRecords
(htmlPage, itemKey)`を`web/util`に新設し、各`20xxx`本体フラグメントはこれを用いて自身が
必要とする画面表示項目を取得する(`htmlPage`自体はModel属性としてどのフラグメントからも
参照可能なため、パートをまたいだ検索が可能)。一貫性のため、同一パート内に自身の項目が
ネストされている場合も含め、全ての本体フラグメントでこのユーティリティを使用する。

### JSP→Thymeleaf変換対応表

| JSP | Thymeleaf |
|---|---|
| `<%@ include file="..." %>` | `th:replace="~{parts/... :: ...}"` |
| スクリプトレットの`if` | `th:if` |
| `request.getAttribute(...)` | Model属性(`TaskallV2Controller`が`populateModel`等で設定済み) |
| `<%= account.get(0).get("ACCOUNT_NAME") %>` | `th:text="${account[0].ACCOUNT_NAME}"` |
| `AuthUtil.hasReadAuth(...)` | `T(com.freedom.taskall_v2.web.util.AuthUtil).hasReadAuth(...)` |

## テスト方針

- 既存規約通り、`LoginService`/`ErrMsgService`/`AuthUtil`それぞれに対応するテストクラスを
  Mockito(`@ExtendWith(MockitoExtension.class)`)で新設する。DBアクセスは`RecordQueryService`を
  モック化する。
- `CreateHtmlService`の既存テストクラスに、`errMsgKey`デフォルト化・`respKind`/`destination`の
  上書き防止ガードに関するテストケースを追加する。
- `TaskallV2ControllerTest`に`myPage.html`の`@GetMapping`/`@PostMapping`に関するテストケースを
  追加する。

## ドキュメント更新

- `.github/copilot-instructions.md`に、今後の画面移植issue向けの一般的な移植手順を追記する:
  1. 移植元JSPの `10xxx`(権限確認)/`20xxx`(本体)ペアを洗い出す。
  2. 対応するThymeleafフラグメントを `templates/parts/`(ラッパー)・
     `templates/parts/common/`(本体)配下に作成する。
  3. 必要なDBデータ(`SCR_ELM`のサービスクラス名等)を`com.freedom.taskall_v2.*`へ更新し、
     `DbSchemaSqlGeneratorRealDataTest`でSQLを再生成する。
  4. 必要なバックエンドサービスを移植する(例外方針・JSON入出力方針は本プロジェクトの規約に
     合わせて書き直す)。
  5. 各サービスにMockitoベースの対応するテストクラスを追加する。
