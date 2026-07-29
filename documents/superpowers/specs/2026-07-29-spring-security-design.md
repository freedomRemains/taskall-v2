# SpringSecurity導入 設計書

---

## 背景・目的

issue #7 対応。現状のマイページ(`/taskall-v2/service/myPage.html`)は、`LoginService`が
`ACCNT`テーブルのメールアドレス・パスワードを直接照合する簡易的な認証を行っている。
パスワードは平文比較のままであり(`LoginService`内に`// TODO ハッシュ化した値を比較する`
というコメントが残っている)、CSRF対策も未実装である。

本設計書は、この認証の仕組みにSpringSecurityを適用し、以下を実現することを目的とする。

- 認証処理(ログイン・ログアウト・パスワードハッシュ化)をSpringSecurityの標準機構に委譲する
- フォーム送信全般にCSRFトークンを行き渡らせ、CSRF対策を得る
- 将来のメールによる2段階認証を差し込める拡張ポイントを整備する(本issueでは実装しない)

以下は明確にスコープ外とする。

- 2段階認証(メールによるワンタイムコード送信・検証)の実装自体
- 画面パーツ単位のread/edit権限(`HTML_PARTS_IN_APROLE`)・ロール制約(`REQUIRE_APROLE`)を
  SpringSecurityの認可機構(`hasRole`等)へ置き換えること

## 前提・制約

- 認可判定(ロール制約・画面パーツ単位read/edit権限)は、複雑化を避けるため引き続き
  `GetAccountService`/`AuthUtil`による独自実装に全面的に委ねる。SpringSecurity側は
  `authorizeHttpRequests`を`anyRequest().permitAll()`とし、認可には関与しない。
- 既存の「DBレコード駆動」ルーティング(`URI_PATTERN`→`HTML_PAGE`→`SCR`→`SCR_ELM`)は
  維持する。ログイン(POST)・ログアウトの2つのURLのみ、SpringSecurityの各種フィルタが
  コントローラより先に横取りする形にする。
- `ACCNT.PASSWORD`カラムは`VARCHAR(256)`であり、BCryptハッシュ値(60文字程度)を
  格納するのに十分な長さがある。

## 採用するアプローチ

### ログインフロー

既存の`POST /taskall-v2/service/myPage.html`をそのまま`loginProcessingUrl`として
再利用する。

比較した他アプローチ:

- ログイン専用の新規URL(例: `/login`)を新設する案。既存ルーティングとは独立するが、
  現在1つの共有`<form id="mainForm">`内にログインフォームが同居している画面構成
  (`10030_login.html`)を崩し、ログイン専用フォームを別途用意する必要が生じるため、
  改修範囲が広がる。issueの「所定のURIパターンをSpringSecurityのログインに紐づける」
  という記載にも合致しないため不採用。

具体的な変更内容:

- `AccountUserDetailsService`(`UserDetailsService`実装)を新設し、`ACCNT`テーブルを
  `MAIL_ADDRESS`で検索する。取得した`ACCNT_ID`を保持する独自`UserDetails`実装
  (`AccountPrincipal`)を返す。パスワード照合は`BCryptPasswordEncoder`で行う。
- `AccountAuthenticationSuccessHandler`(`AuthenticationSuccessHandler`実装)を新設する。
  認証成功時にセッション属性`accountId`へ`ACCNT_ID`を設定し、
  `GET /taskall-v2/service/myPage.html`へリダイレクトする(旧`LoginService`成功時の
  `respKind=redirect, destination=myPage.html`と同じ着地点)。将来のメール2段階認証を
  差し込むための拡張ポイントであることをコメントで明記する(本issueでは実装しない)。
- `AccountAuthenticationFailureHandler`(`AuthenticationFailureHandler`実装)を新設する。
  認証失敗時、既存の`ErrMsgService`を使い同一の`GNR_KEY_VAL_ID`(`1000401`)で
  エラーメッセージキーを発行し、`myPage.html?errMsgKey=<キー>`へリダイレクトする
  (旧`LoginService`失敗時の挙動を完全に踏襲する)。
- 旧`LoginService`及び対応するテストクラスを削除する。
- DBデータ(`SCR_ELM`)から`LoginService`に対応する行(`SCR_ELM_ID=1100251`)を削除し、
  `SCR`(`SCR_ID=1100251`)配下は`GetAccountService`/`CreateHtmlService`のみとする。
  `HTML_PAGE`のマイページ行(`HTML_PAGE_ID=1000201`)は`SCR_ID_POST`を`0`(無効)に変更し、
  `VERSION`を+1、`UPDATED_AT`を更新日にする(POSTはSpringSecurityのフィルタが
  完結させるため、コントローラ経由のスクリプト実行は不要となるため)。
- `TaskallV2Controller`の`postMyPage`メソッドを削除する(SpringSecurityの
  `UsernamePasswordAuthenticationFilter`がコントローラより先にリクエストを処理するため、
  到達不能コードになる)。

### ログアウトフロー

`SecurityConfig`で以下を設定する。

- `logoutUrl("/taskall-v2/service/logout.html")`
- `logoutSuccessUrl("/taskall-v2/service/myPage.html")`(実装計画時にログイン画面への
  誘導を優先する方針へ変更。当初案の`top.html`から変更した)
- セッション無効化・認証情報クリア・Cookie削除

ログイン中のみ表示される独立した小さな`<form th:action="@{/taskall-v2/service/logout.html}"
method="post">`(CSRFトークンの隠しフィールドを同梱)によるログアウトボタンを追加する。
当初は`20040_commonLinkList.html`部品への追加を想定していたが、同部品は既存の
`mainForm`内でレンダリングされ、HTMLは`<form>`の入れ子を許容しないため、実装時に
`10000_contents.html`側で`mainForm`と兄弟要素として追加する方式に変更した。

比較した他アプローチ:

- 既存の`LNK`/`mainForm`駆動の仕組み(`IS_POST=1`のリンクは常に「表示中ページ」へ
  POSTする挙動)にログアウトを乗せる案。ログアウトは`HTML_PAGE`に対応しない特殊URLで
  あり、この仕組みに乗せると「表示中ページへのPOST」という前提が崩れるため、
  独立したフォームで実装する方がシンプルで分かりやすいと判断し不採用。

### CSRF対応

SpringSecurity導入によりCSRF保護がデフォルトで有効化される。共通の
`10000_contents.html`内`<form id="mainForm" method="POST">`(アクション属性なし、
常に表示中ページへPOSTする)に、以下の隠しフィールドを1箇所追加する。

```html
<input type="hidden" th:if="${_csrf}" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
```

これにより、テーブルメンテナンス等の既存POST操作すべてにCSRFトークンが行き渡る。

比較した他アプローチ:

- `thymeleaf-extras-springsecurity6`ライブラリを導入し、`th:action`を持つ全フォームに
  自動でCSRF隠しフィールドを注入させる案。今回はフォームが`mainForm`(共有)と
  ログアウト専用フォームの2箇所のみであり、追加ライブラリを導入するほどの規模でないため
  不採用。手動での隠しフィールド追加のみで十分シンプルに対応できる。

### パスワードハッシュ化

`ACCNT.PASSWORD`を平文からBCryptハッシュへ移行する(既存コードの
`// TODO ハッシュ化した値を比較する`を解消する)。`src/main/resources/db/data/ACCNT.txt`の
既存データ行を、`VERSION`を+1、`UPDATED_AT`を更新日にした上でBCryptハッシュ値に置き換える。

## コンポーネント構成

- `com.freedom.taskall_v2.web.config.SecurityConfig`
  - `SecurityFilterChain`Bean。`authorizeHttpRequests`(`permitAll`)、`formLogin`
    (`loginProcessingUrl`/`usernameParameter`/`passwordParameter`/
    `successHandler`/`failureHandler`)、`logout`の設定を持つ。
  - `PasswordEncoder`Bean(`BCryptPasswordEncoder`)。
- `com.freedom.taskall_v2.web.security.AccountUserDetailsService`
  - `UserDetailsService`実装。`RecordQueryService`経由で`ACCNT`テーブルを
    `MAIL_ADDRESS`で検索し、`AccountPrincipal`を返す。該当0件は
    `UsernameNotFoundException`をスローする。
- `com.freedom.taskall_v2.web.security.AccountPrincipal`
  - `UserDetails`実装。`ACCNT_ID`・`MAIL_ADDRESS`・ハッシュ化済み`PASSWORD`を保持する。
    権限(`GrantedAuthority`)は認可を独自実装に委ねるため空リストとする。
- `com.freedom.taskall_v2.web.security.AccountAuthenticationSuccessHandler`
  - `AuthenticationSuccessHandler`実装。セッション属性`accountId`の設定と
    マイページへのリダイレクトを行う。
- `com.freedom.taskall_v2.web.security.AccountAuthenticationFailureHandler`
  - `AuthenticationFailureHandler`実装。`ErrMsgService`経由でエラーメッセージキーを
    発行し、エラー付きリダイレクトを行う。

## データフロー

1. ユーザーが`myPage.html`の共有`mainForm`(CSRFトークン付き)から`MAIL_ADDRESS`/
   `PASSWORD`を送信する。
2. SpringSecurityの`UsernamePasswordAuthenticationFilter`が`POST
   /taskall-v2/service/myPage.html`を横取りし、`AccountUserDetailsService`と
   `PasswordEncoder`による認証を行う(`TaskallV2Controller`には到達しない)。
3. 認証成功時: `AccountAuthenticationSuccessHandler`がセッションへ`accountId`を設定し、
   `GET myPage.html`へリダイレクトする。以降は既存の`GetAccountService`が
   セッションの`accountId`を読み取り、権限一覧・アカウント情報を構築する
   (この経路は今回変更しない)。
4. 認証失敗時: `AccountAuthenticationFailureHandler`が`ErrMsgService`でエラーメッセージ
   キーを発行し、`myPage.html?errMsgKey=<キー>`へリダイレクトする。
5. ログアウト時: 独立したログアウト用フォームから`POST
   /taskall-v2/service/logout.html`を送信すると、SpringSecurityの`LogoutFilter`が
   セッション無効化・認証情報クリアを行い、`myPage.html`(ログイン画面)へリダイレクト
   する。

## エラーハンドリング

- 認証失敗(該当アカウント無し・パスワード不一致のいずれも)は、SpringSecurityの
  `AuthenticationException`として`AccountAuthenticationFailureHandler`に集約される。
  既存の業務/システム例外方針(`BusinessRuleViolationException`/
  `ApplicationInternalException`)とは別軸の、SpringSecurity標準の例外処理経路を使う。
- `AccountUserDetailsService`で対象アカウントが見つからない場合は
  `UsernameNotFoundException`をスローする(SpringSecurity側で`AuthenticationException`
  として扱われ、`AccountAuthenticationFailureHandler`に到達する)。

## テスト方針

- `AccountUserDetailsServiceTest`: Mockitoで`RecordQueryService`をモック化し、
  該当アカウント有無それぞれのケースを検証する。
- `AccountAuthenticationSuccessHandlerTest`/`AccountAuthenticationFailureHandlerTest`:
  Mockitoでセッション属性設定・リダイレクト先・`ErrMsgService`呼び出しを検証する。
- ログイン成功/失敗/ログアウトの一連の流れは、`@SpringBootTest`+`MockMvc`による
  統合テストで、実際のSpringSecurityフィルタチェーンが適用されることを確認する。
- 既存`TaskallV2ControllerTest`のPOST myPage関連テスト(旧`LoginService`経由の
  ケース)は、上記の新テストへ移設・整理する。

## 影響範囲まとめ

- 追加: `SecurityConfig`, `AccountUserDetailsService`, `AccountPrincipal`,
  `AccountAuthenticationSuccessHandler`, `AccountAuthenticationFailureHandler`、
  及び各テストクラス。ログアウト用フォームのテンプレート追加。
- 変更: `build.gradle`(`spring-boot-starter-security`追加)、
  `TaskallV2Controller`(`postMyPage`削除)、
  `10000_contents.html`(CSRF隠しフィールド追加、`mainForm`と兄弟の独立した
  ログアウトフォーム追加。`20040_commonLinkList.html`は`mainForm`内にネストされる
  ため、`<form>`の入れ子を避けるべくこちらに配置した)、
  `src/main/resources/db/data/ACCNT.txt`(パスワードハッシュ化)、
  `src/main/resources/db/data/HTML_PAGE.txt`(`SCR_ID_POST`を`0`に変更)、
  `SCR.txt`/`SCR_ELM.txt`(旧POSTログイン用スクリプト定義を削除)。
- 削除: `LoginService`及びそのテストクラス、`SCR_ELM`の該当行
  (`SCR_ELM_ID=1100251`)。
