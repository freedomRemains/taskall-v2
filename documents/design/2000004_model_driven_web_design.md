# モデル駆動Web実装設計(issue #9)

## 目的

`TaskallV2Controller` の仮実装を廃止し、DBレコード(`URI_PATTERN`/`HTML_PAGE`/`SCR`/`SCR_ELM`/`SCR_PRM`/
`PARTS_IN_PAGE`/`HTML_PARTS`/`PARTS_ITEM`)に基づいてTOPページ表示ができるようにする。
移植元「remainz」の `ServiceControlServlet`/`ScriptService`/`GetAccountService`/`CreateHtmlService` を
Spring Boot + Thymeleaf向けに移植する。対象issueは
[#9](https://github.com/freedomRemains/taskall-v2/issues/9)。

## スコープ

- TOPページ(`GET /taskall-v2/service/top.html`)が、DBレコード駆動で表示できること。
- エラー発生時のエラーページ制御(forward/redirect)は**スコープ外**とし、別issueで対応する。
  例外はグローバル例外ハンドラ(`BusinessRuleViolationException`/`ApplicationInternalException`/
  `Exception`)に委譲する。

## DBデータ移行

- `TBL_DEF.txt` に `PARTS_ITEM` 定義を `PARTS_IN_PAGE` の直後、`TBL_DEF_ID=1001001` として追加し、
  以降(`APROLE`以降)の既存IDを100ずつずらす。
- `src/main/resources/db/data/PARTS_ITEM.txt`、`SCR_PRM.txt` を移植元
  `src/test/resources/service/script/dbmng/h2/20_dbdata/10_authorized/` からコピーする。
  - `PARTS_ITEM.txt` は `PARTS_IN_PAGE_ID >= 1001501` のレコード(76〜93行目)を削除する
    (v2側に存在しない画面に対応するレコードのため)。
- SQL生成(CREATE/DROP/INSERT/SELECT)及び初期データ再投入の対象にこの2テーブルを追加し、
  関連するテストクラスを修正する。

## DBアクセス層

- 移植元の `GenericDb`/`DbInterface` に相当する仕組みとして、`common/db` に `DataSource` を注入し
  `ArrayList<LinkedHashMap<String,String>>` を返す SELECT専用クラス(例: `RecordQueryService`)を
  新設する。
- commit/rollbackは呼び出し側(コントローラの共通処理メソッド)に付与する `@Transactional` で
  Spring管理とし、明示的なcommit/rollbackコードを書かない。
- SQLite/MySQLの差異が生じた場合に備え、既存の `common/db/sqlite` パッケージ分割方針を踏襲する
  (差異が今回発生しなければ新設しない)。

## スクリプト実行

- 移植元 `ScriptService` に相当する `ScriptExecutionService`(仮称)を `common/service/script` に
  新設する。`SCR`/`SCR_ELM` をスクリプトIDで結合し `ORD_IN_GRP` 順にサービスを実行する。
- 各サービスは `String doService(String inputJson)` のようにJSON文字列を入出力し、前段の出力が
  次段の入力になる(直前の出力と入力をマージしてから渡す)。
- `SCR_PRM` によるスクリプトパラメータ投入、及び `#{key}` 形式のプレースホルダー置換ロジックを
  移植する。アダプタ処理(`AdapterInterface`/`GenericAdapter`)は移植しない。

## Webサービス

- `GetAccountService`: `web/service` に移植。`accountId` が未指定ならゲストアカウント
  (`ACCNT_ID=1000001`)を採用し、アカウント情報・権限一覧(`authList`)を出力する。
  ロール制約違反時は例外をスローする(エラーページ制御自体はスコープ外)。
- `CreateHtmlService`: `web/service` に移植。`PARTS_IN_PAGE`/`HTML_PARTS`/`PARTS_ITEM` を結合して
  ページ内パーツと画面表示項目を取得する。

### 出力JSON構造の改善

移植元は `ITEM_KEY` をトップレベルのキーとして展開するため、同一画面に同じパーツが2つ以上あると
衝突する問題がある。本移植では `htmlPage` 配列の各要素(`PARTS_IN_PAGE_ID`単位)に、そのパーツの
画面表示項目を `items` 配列としてネストする構造に変更する。

```json
{
  "respKind": "forward",
  "destination": "10000_contents",
  "htmlPage": [
    {
      "partsInPageId": "1000001",
      "htmlPartsId": "1000001",
      "partsName": "システム名",
      "items": [
        { "itemKey": "systemName", "records": [ { "GNR_VAL": "Remainz" } ] }
      ]
    },
    {
      "partsInPageId": "1000002",
      "htmlPartsId": "1000002",
      "partsName": "共通ヘッダ",
      "items": [
        { "itemKey": "urlLink", "records": [ { "PAGE_NAME": "TOP", "URI_PATTERN": "..." } ] }
      ]
    }
  ]
}
```

クライアント(Thymeleaf)側は `htmlPage` をループし、`items` からキーで値を取得するだけでよい
シンプルな実装とする。

## コントローラ

- `TaskallV2Controller#getTop` を、移植元 `controllService` に相当する共通処理呼び出しに変更する。
- 共通処理(仮称 `RequestHandlingService`)の流れ:
  1. `HttpServletRequest` の属性・ヘッダ・パラメータをログ記録する(移植元 `ServiceControlServlet`
     の `setRequestParameterAsInput` 相当)。
  2. リクエストURIで `URI_PATTERN` を検索し、`HTML_PAGE` と結合して `SCR_ID_GET` を取得する。
  3. `ScriptExecutionService` で `GetAccountService` → `CreateHtmlService` の順に実行する。
  4. 出力の `RESP_KIND_GET` が `forward` ならThymeleafビュー名を、`redirect` なら
     `redirect:` プレフィックス付きの遷移先を返す。
- 共通処理メソッドには `@Transactional` を付与し、commit/rollbackをSpring管理とする。

## テスト方針

- 各実装クラスにMockitoベースの単体テストを用意する(既存の
  `documents/design/2000003_implementation_design.md` の方針を踏襲)。
- 直下の協働クラスのみをモックし、多段呼び出しはモック経由で検証する。
