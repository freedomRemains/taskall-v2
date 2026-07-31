# 二段階認証(二段階ログイン) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** メールアドレス及びパスワードによる既存のSpringSecurityログインに、6桁パスコードのメール送信による二段階認証を追加し、issue #11・`documents/design/2000006_two_phase_login.md`(承認済み設計書)の仕様を実装する。

**Architecture:** 「ログイン中」の判定は既存通りHTTPセッションの`accountId`属性(`GetAccountService`/`AuthUtil`が参照)で行う。一次認証(メールアドレス+パスワード)のPOSTは`TaskallV2Controller`より手前でSpringSecurityの`UsernamePasswordAuthenticationFilter`が横取りするため、ロックチェック・`LOGIN_STATUS`登録・パスコードメール送信は、既存の`AccountUserDetailsService`+`DaoAuthenticationProvider`を置き換える新規`TwoPhaseAuthenticationProvider`(`AuthenticationProvider`実装)に実装する。一次認証成功時点では`accountId`をセッションへ格納せず(＝まだログイン扱いにしない)、`pendingTwoFactorAccountId`という別セッション属性のみ設定して二次認証画面へ遷移させる。二次認証(6桁コード)のPOSTは、DBレコード駆動の既存エンジンへ新規`ScriptElementService`として実装し、成功時のみ`accountId`をセッションへ格納する。

**Tech Stack:** Java 21, Spring Boot(spring-boot-starter-security, spring-boot-starter-mail, spring-boot-starter-jdbc, spring-boot-starter-thymeleaf), SQLite(`org.xerial:sqlite-jdbc`), Jackson(`tools.jackson.*`), JUnit5 + Mockito(単体テスト)。

## Global Constraints

- 認可判定(`AuthUtil.hasReadAuth`/`hasEditAuth`)は変更しない。SpringSecurityの`authorizeHttpRequests`は引き続き`anyRequest().permitAll()`のみとする。
- 一次認証成功でもまだログインと扱わない。制御用テーブル`LOGIN_STATUS`に`first_auth_pass`ステータスを登録するにとどめる(`documents/design/2000006_two_phase_login.md`「二段階認証の処理概略」節)。
- 二段階認証メールのタイトルは「二段階認証パスコード」、本文は「次の6桁の数字をご入力ください。\n  XXX XXX」(改行後に半角スペース2文字、3文字、半角スペース、3文字の6桁数字)とする(設計書同節)。
- パスコードの有効期限は5分。二次認証を5回誤ると、アカウントを15分間ロックする(設計書同節)。
- 平文の6桁パスコードはDBに保存しない。`PASSCODE_HASH`カラムへ一方向ハッシュ化した値のみ保存する(設計書「制御用のテーブル追加」節)。
- `LOGIN_STATUS`は「1回のログイン試行(=1ブラウザセッション)」単位、`ACCNT_AUTH_LOCK`は「アカウント単位で1行のみ」に役割を分離する。両テーブルの識別・集計をこの単位から変更しない(設計書「制御用のテーブル追加」節)。
- `LOGIN_STATUS`は毎回物理削除して作り直さず、既存レコードがあれば更新する(設計書冒頭差分説明)。
- `LOGIN_STATUS`の検索キーには`(ACCNT_ID, SESSION_ID)`の一意制約を付与し、別セッションの二次認証パスコードを使い回す攻撃を防止する(設計書同節)。
- `ACCNT_AUTH_LOCK.LOCKED_UNTIL`がNULL、または現在時刻より過去の場合は「ロックされていない」ものとして扱う(設計書「アカウント認証ロック」節)。ロックが自然失効したことを検知した場合、`FAIL_CNT`を0へリセットする(設計書に明記はないが、失効後も失敗回数が残存し続けると次の1回の誤入力で即再ロックされてしまう論理的な欠陥を避けるための補完。設計書レビュー完了後に見つかった点のため、本プラン内で明示する)。
- `src/main/resources/db/data`配下の`.txt`編集後は必ず`DbSchemaSqlGeneratorRealDataTest`を実行し`src/main/resources/db/sql`配下のSQLを再生成してコミットする。
- DBデータ編集時は`VERSION`を+1(新規行は`VERSION=1`)、`UPDATED_AT`を更新日`00:00:00`にする。`CREATED_BY`/`UPDATED_BY`は`data_loader`。
- ローカルの`taskallv2.db`(SQLiteファイル、`.gitignore`対象)は初回起動時のみ`TBL_DEF`テーブルの有無で自動初期化される(`DbInitializer`)。`db/data`の内容を変更した場合、ファイルを削除してから次回テスト実行時に再生成させること。
- 実装クラスには必ずペアとなるテストクラスを用意する。
- `ERROR`/`WARN`ログの文字列は`src/main/resources/msg/messages.properties`にメッセージキーとして定義し`MsgUtil#get`経由で取得する。`INFO`ログはこの限りではない。
- 5〜10行程度の処理のかたまりごとに、概要を説明するブロックコメントを付与する。
- テスト・ビルドコマンドは以下のプレフィックスを必ず使用する:
  `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew ...`

---

## Task 1: CreateTableSqlBuilderにEXTRA列のUNIQUE_x_y記法を実装する

**Files:**
- Modify: `src/main/java/com/freedom/taskall_v2/common/db/CreateTableSqlBuilder.java`
- Test: `src/test/java/com/freedom/taskall_v2/common/db/CreateTableSqlBuilderTest.java`

**Interfaces:**
- Consumes: 既存の`build(String tableName, List<Map<String,String>> columnDefs)`(`columnDefs`の各行に`EXTRA`キーが追加される)。
- Produces: `EXTRA`列に`UNIQUE_<グループ番号>_<グループ内順序>`(例: `UNIQUE_1_1`/`UNIQUE_1_2`)を持つカラムが2件以上あれば、CREATE TABLE文の末尾に`,\n    UNIQUE (col1, col2)`をグループ番号の昇順・グループ内はグループ内順序の昇順で追加する。以降のタスク(Task 2)はこの機能を前提に`LOGIN_STATUS`/`ACCNT_AUTH_LOCK`のCREATE TABLE文を生成する。

- [ ] **Step 1: 失敗するテストを書く**

`CreateTableSqlBuilderTest.java`に以下のテストメソッドを追加する(既存のテストメソッド・`buildColumnDef`ヘルパーの下に追記)。

```java
    @Test
    void EXTRA列にUNIQUE記法がある場合は複合UNIQUE制約が末尾に出力されること() {

        List<Map<String, String>> columnDefs = List.of(
                buildColumnDefWithExtra("ID", "INT", "NO", "PRI", "null", "AUTO_INCREMENT"),
                buildColumnDefWithExtra("ACCNT_ID", "INT", "YES", "", "null", "UNIQUE_1_1"),
                buildColumnDefWithExtra("SESSION_ID", "VARCHAR(256)", "YES", "", "null", "UNIQUE_1_2"));

        String sql = createTableSqlBuilder.build("SAMPLE", columnDefs);

        assertThat(sql).isEqualTo("CREATE TABLE IF NOT EXISTS SAMPLE (\n"
                + "    ID INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    ACCNT_ID INT,\n"
                + "    SESSION_ID VARCHAR(256),\n"
                + "    UNIQUE (ACCNT_ID, SESSION_ID)\n"
                + ");");
    }

    @Test
    void EXTRA列が空の場合はUNIQUE制約が出力されないこと() {

        List<Map<String, String>> columnDefs = List.of(
                buildColumnDefWithExtra("ID", "INT", "NO", "PRI", "null", "AUTO_INCREMENT"),
                buildColumnDefWithExtra("NAME", "TEXT", "YES", "", "null", ""));

        String sql = createTableSqlBuilder.build("SAMPLE", columnDefs);

        assertThat(sql).isEqualTo("CREATE TABLE IF NOT EXISTS SAMPLE (\n"
                + "    ID INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    NAME TEXT\n"
                + ");");
    }

    private Map<String, String> buildColumnDefWithExtra(String fieldName, String typeName, String allowNull,
            String keyDiv, String defaultValue, String extra) {
        Map<String, String> columnDef = buildColumnDef(fieldName, typeName, allowNull, keyDiv, defaultValue);
        columnDef.put("EXTRA", extra);
        return columnDef;
    }
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.common.db.CreateTableSqlBuilderTest"`
Expected: FAIL(新規2メソッドが、UNIQUE句なしの現状実装のための出力不一致で失敗する)

- [ ] **Step 3: 最小限の実装を追加する**

`CreateTableSqlBuilder.java`の`build`メソッドを以下のように変更する(既存の`sql.append(columnPart).append("\n);");`の行を置き換える)。

```java
        sql.append(columnPart);
        String uniqueConstraintPart = buildUniqueConstraintPart(columnDefs);
        if (!uniqueConstraintPart.isEmpty()) {
            sql.append(",\n").append(uniqueConstraintPart);
        }
        sql.append("\n);");
        return sql.toString();
    }

    /**
     * EXTRA列の"UNIQUE_<グループ番号>_<グループ内順序>"記法を解釈し、
     * グループごとの複合UNIQUE制約句を生成する。
     */
    private String buildUniqueConstraintPart(List<Map<String, String>> columnDefs) {

        // グループ番号ごとに、グループ内順序をキーとしてカラム名を集める
        Map<Integer, java.util.TreeMap<Integer, String>> groupedColumns = new java.util.TreeMap<>();
        java.util.regex.Pattern uniquePattern = java.util.regex.Pattern.compile("^UNIQUE_(\\d+)_(\\d+)$");

        for (Map<String, String> columnDef : columnDefs) {
            String extra = columnDef.get("EXTRA");
            if (extra == null || extra.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher matcher = uniquePattern.matcher(extra);
            if (!matcher.matches()) {
                continue;
            }
            int groupNo = Integer.parseInt(matcher.group(1));
            int ordInGroup = Integer.parseInt(matcher.group(2));
            groupedColumns.computeIfAbsent(groupNo, key -> new java.util.TreeMap<>())
                    .put(ordInGroup, columnDef.get("FIELD_NAME"));
        }

        // グループ番号の昇順に、"UNIQUE (col1, col2)"句を連結する
        StringBuilder uniquePart = new StringBuilder();
        for (java.util.TreeMap<Integer, String> columns : groupedColumns.values()) {
            if (uniquePart.length() > 0) {
                uniquePart.append(",\n");
            }
            uniquePart.append("    UNIQUE (").append(String.join(", ", columns.values())).append(")");
        }
        return uniquePart.toString();
    }
```

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.common.db.CreateTableSqlBuilderTest"`
Expected: BUILD SUCCESSFUL(5件のテストが全て成功する)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/common/db/CreateTableSqlBuilder.java src/test/java/com/freedom/taskall_v2/common/db/CreateTableSqlBuilderTest.java
git commit -m "feat: TBL_DEFのEXTRA列にUNIQUE_x_y記法による複合UNIQUE制約生成を追加"
```

---

## Task 2: LOGIN_STATUS/ACCNT_AUTH_LOCKテーブルのDB定義・データ資材を追加する

**Files:**
- Modify: `src/main/resources/db/data/TBL_DEF.txt`(末尾に追記)
- Create: `src/main/resources/db/data/LOGIN_STATUS.txt`(ヘッダ行のみ、初期データなし)
- Create: `src/main/resources/db/data/ACCNT_AUTH_LOCK.txt`(ヘッダ行のみ、初期データなし)
- Test: 既存の`DbSchemaSqlGeneratorRealDataTest`(新規テストクラスは追加しない。既存テストを再実行してSQL生成を検証する)

**Interfaces:**
- Consumes: Task 1で実装した`CreateTableSqlBuilder`のUNIQUE制約生成ロジック。
- Produces: SQLiteの`LOGIN_STATUS`/`ACCNT_AUTH_LOCK`テーブル(以降のTask 4〜9が`JdbcTemplate`経由でこれらのテーブルへSELECT/INSERT/UPDATE/DELETEする)。

- [ ] **Step 1: TBL_DEF.txtへ2テーブル分のカラム定義行を追記する**

`src/main/resources/db/data/TBL_DEF.txt`の末尾(現在の最終行`1002114	ATTR	UPDATED_AT	...`の次の行)に、以下をタブ区切りで追記する(各列は`TBL_DEF_ID / TABLE_NAME / FIELD_NAME / TYPE_NAME / ALLOW_NULL / KEY_DIV / DEFAULT_VALUE / EXTRA / TABLE_LOGICAL_NAME / FIELD_LOGICAL_NAME / FOREIGN_TABLE / DESC_FIELD`の順)。

```
1002201	LOGIN_STATUS	LOGIN_STATUS_ID	INT	NO	PRI	null	AUTO_INCREMENT	ログイン試行	ログイン試行ID		LOGIN_STATUS_ID
1002202	LOGIN_STATUS	ACCNT_ID	INT	YES		null	UNIQUE_1_1	ログイン試行	アカウントID	ACCNT	
1002203	LOGIN_STATUS	SESSION_ID	VARCHAR(256)	YES		null	UNIQUE_1_2	ログイン試行	セッションID		
1002204	LOGIN_STATUS	CURRENT_STATUS	VARCHAR(256)	YES		null		ログイン試行	現在ステータス		
1002205	LOGIN_STATUS	PASSCODE_HASH	VARCHAR(256)	YES		null		ログイン試行	パスコードハッシュ		
1002206	LOGIN_STATUS	EXPIRES_AT	TIMESTAMP	YES		null		ログイン試行	有効期限		
1002207	LOGIN_STATUS	VERSION	INT	YES		null		ログイン試行	バージョン		
1002208	LOGIN_STATUS	IS_DELETED	INT	YES		null		ログイン試行	削除フラグ		
1002209	LOGIN_STATUS	CREATED_BY	VARCHAR(128)	YES		null		ログイン試行	作成者		
1002210	LOGIN_STATUS	CREATED_AT	DATETIME	YES		null		ログイン試行	作成日時		
1002211	LOGIN_STATUS	UPDATED_BY	VARCHAR(128)	YES		null		ログイン試行	更新者		
1002212	LOGIN_STATUS	UPDATED_AT	TIMESTAMP	YES		null		ログイン試行	更新日時		
1002301	ACCNT_AUTH_LOCK	ACCNT_AUTH_LOCK_ID	INT	NO	PRI	null	AUTO_INCREMENT	アカウント認証ロック	アカウント認証ロックID		ACCNT_AUTH_LOCK_ID
1002302	ACCNT_AUTH_LOCK	ACCNT_ID	INT	YES		null	UNIQUE_1_1	アカウント認証ロック	アカウントID	ACCNT	
1002303	ACCNT_AUTH_LOCK	FAIL_CNT	INT	YES		null		アカウント認証ロック	認証失敗回数		
1002304	ACCNT_AUTH_LOCK	LOCKED_UNTIL	TIMESTAMP	YES		null		アカウント認証ロック	ロック解除予定時刻		
1002305	ACCNT_AUTH_LOCK	VERSION	INT	YES		null		アカウント認証ロック	バージョン		
1002306	ACCNT_AUTH_LOCK	IS_DELETED	INT	YES		null		アカウント認証ロック	削除フラグ		
1002307	ACCNT_AUTH_LOCK	CREATED_BY	VARCHAR(128)	YES		null		アカウント認証ロック	作成者		
1002308	ACCNT_AUTH_LOCK	CREATED_AT	DATETIME	YES		null		アカウント認証ロック	作成日時		
1002309	ACCNT_AUTH_LOCK	UPDATED_BY	VARCHAR(128)	YES		null		アカウント認証ロック	更新者		
1002310	ACCNT_AUTH_LOCK	UPDATED_AT	TIMESTAMP	YES		null		アカウント認証ロック	更新日時		
```

注意: `ACCNT_ID`のFOREIGN_TABLEは`ACCNT`とする(既存テーブルへの参照)。`DESC_FIELD`列は`PRI`行にのみ、テーブル自身の代表フィールド名(自己参照)を設定する(既存の`ATTR_ID`行等の慣例に合わせる)。

- [ ] **Step 2: 新規テーブルのデータファイル(ヘッダ行のみ)を作成する**

`src/main/resources/db/data/LOGIN_STATUS.txt`を新規作成する(中身はヘッダ行のみ。初期データは投入しない):

```
LOGIN_STATUS_ID	ACCNT_ID	SESSION_ID	CURRENT_STATUS	PASSCODE_HASH	EXPIRES_AT	VERSION	IS_DELETED	CREATED_BY	CREATED_AT	UPDATED_BY	UPDATED_AT
```

`src/main/resources/db/data/ACCNT_AUTH_LOCK.txt`を新規作成する(同様にヘッダ行のみ):

```
ACCNT_AUTH_LOCK_ID	ACCNT_ID	FAIL_CNT	LOCKED_UNTIL	VERSION	IS_DELETED	CREATED_BY	CREATED_AT	UPDATED_BY	UPDATED_AT
```

- [ ] **Step 3: ローカルのSQLiteファイルを削除し、テストで再生成させる**

Run: `rm -f /home/develop/taskall-v2/taskallv2.db`

(このファイルは`TBL_DEF`テーブルの有無で初回起動時にのみ自動初期化されるため、`db/data`の内容を変更した場合は削除しておく必要がある)

- [ ] **Step 4: DbSchemaSqlGeneratorRealDataTestを実行しSQLを再生成する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.common.db.DbSchemaSqlGeneratorRealDataTest"`
Expected: BUILD SUCCESSFUL。`src/main/resources/db/sql`配下に`LOGIN_STATUS`/`ACCNT_AUTH_LOCK`用のCREATE/INSERT/SELECT SQLファイルが新規生成されること、また既存ファイルの内容が変わらないこと(既存テーブルのSQLが意図せず変化していないか`git diff`で確認する)。

- [ ] **Step 5: 生成されたSQLにUNIQUE制約が含まれることを確認する**

Run: `cat src/main/resources/db/sql/CREATE_LOGIN_STATUS.sql`
Expected: `CREATE TABLE`文の末尾付近に`UNIQUE (ACCNT_ID, SESSION_ID)`が含まれること。同様に`cat src/main/resources/db/sql/CREATE_ACCNT_AUTH_LOCK.sql`で`UNIQUE (ACCNT_ID)`が含まれることも確認する。

- [ ] **Step 6: 全体テストを実行し既存テストに影響がないことを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミットする**

```bash
git add src/main/resources/db/data/TBL_DEF.txt src/main/resources/db/data/LOGIN_STATUS.txt src/main/resources/db/data/ACCNT_AUTH_LOCK.txt src/main/resources/db/sql/
git commit -m "feat: LOGIN_STATUS/ACCNT_AUTH_LOCKテーブルのDB定義・データ資材を追加"
```

---

## Task 3: 二段階認証用のエラーメッセージをmessages.properties/GNR_KEY_VAL.txtへ追加する

**Files:**
- Modify: `src/main/resources/msg/messages.properties`
- Modify: `src/main/resources/db/data/GNR_KEY_VAL.txt`

**Interfaces:**
- Consumes: なし(マスタデータ・プロパティファイルの追記のみ)。
- Produces: 以降のTaskが参照する`GNR_KEY_VAL_ID`定数群: `1000402`(アカウントロック中エラー)、`1000403`(二段階認証有効期限切れエラー)、`1000404`(二段階認証コード不一致エラー)。ログ用メッセージキー`msg.err.web.twoFactor.mailSendFailed`、`msg.warn.web.twoFactor.accountLockExpired`。

- [ ] **Step 1: messages.propertiesへ新規メッセージキーを追記する**

`src/main/resources/msg/messages.properties`の末尾に追記する:

```
msg.err.web.twoFactor.mailSendFailed=二段階認証パスコードのメール送信に失敗しました。mailAddress={0}
msg.warn.web.twoFactor.accountLockExpired=アカウント認証ロックの期限切れを検知したため、失敗回数をリセットします。accountId={0}
```

- [ ] **Step 2: GNR_KEY_VAL.txtへ新規エラーメッセージ行を追記する**

`src/main/resources/db/data/GNR_KEY_VAL.txt`の末尾(`1000401	loginError	...`の次の行)に、既存の`loginError`と同じ`GNR_GRP_ID=1000401`(ログインエラーグループ)へ追記する:

```
1000402	accountLockError	アカウントは現在、ロックされています。#Yn#しばらくしてから再度アクセスしてください。	1000401	2	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1000403	twoFactorExpiredError	認証の有効期限が切れました。#Yn#再度、メールアドレス及びパスワードによる認証をお願いします。	1000401	3	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1000404	twoFactorCodeError	入力された数字が正しくありません。再度ご入力ください。	1000401	4	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

注意: TSVファイルは改行区切りのため、値に実際の改行を含めることはできない。本プロジェクトの`TsvValueEscaper`(`src/main/java/com/freedom/taskall_v2/common/db/TsvValueEscaper.java`)は、書き込み前にCR/LF/タブをそれぞれ`#Yr#`/`#Yn#`/`#Yt#`という予約マーカー文字列に変換し、読み込み後に元へ復元する方式を採る。そのため、改行を含むメッセージをTSVへ記述する際は、上記の通り実際の改行文字ではなく`#Yn#`マーカーをそのまま記述すること。

- [ ] **Step 3: ローカルのSQLiteファイルを削除し、テストで再生成させる**

Run: `rm -f /home/develop/taskall-v2/taskallv2.db`

- [ ] **Step 4: DbSchemaSqlGeneratorRealDataTestを実行しSQLを再生成する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.common.db.DbSchemaSqlGeneratorRealDataTest"`
Expected: BUILD SUCCESSFUL。`src/main/resources/db/sql/INSERT_GNR_KEY_VAL.sql`に新規3行分のINSERT文が追加されていることを`cat`で確認する。

- [ ] **Step 5: コミットする**

```bash
git add src/main/resources/msg/messages.properties src/main/resources/db/data/GNR_KEY_VAL.txt src/main/resources/db/sql/
git commit -m "feat: 二段階認証用のエラーメッセージをmessages.properties/GNR_KEY_VALへ追加"
```

---

## Task 4: LoginStatusServiceを実装する(LOGIN_STATUSテーブルの読み書き)

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/service/LoginStatusService.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/service/LoginStatusServiceTest.java`

**Interfaces:**
- Consumes: `RecordQueryService.select(String sql, List<String> params)`(SELECT専用)、`JdbcTemplate`(INSERT/UPDATE/DELETE用、コンストラクタ注入)。
- Produces: 以降のTask 7(`TwoPhaseAuthenticationProvider`)・Task 9(`VerifyTwoFactorAuthService`)・Task 11(定期クリーンアップ)が使用するメソッド群:
  - `LinkedHashMap<String,String> beginAttempt(String accountId, String sessionId)` — `(ACCNT_ID, SESSION_ID)`で検索し、行が無い/期限切れなら新規作成(`not_auth`、有効期限30分後)、有効なら既存行をそのまま返す。戻り値は更新後の行(`LOGIN_STATUS_ID`を含む)。
  - `void markFirstAuthPass(String loginStatusId, String passcodeHash)` — `CURRENT_STATUS='first_auth_pass'`、`EXPIRES_AT`を現在時刻+5分、`PASSCODE_HASH`を設定。
  - `void markFirstAuthFail(String loginStatusId)` — `CURRENT_STATUS='first_auth_fail'`(`EXPIRES_AT`は変更しない)。
  - `java.util.Optional<LinkedHashMap<String,String>> findForVerification(String accountId, String sessionId)` — `(ACCNT_ID, SESSION_ID)`で検索。行が無ければ`Optional.empty()`。期限切れの場合は物理削除した上で`Optional.empty()`。`CURRENT_STATUS`が`first_auth_pass`/`second_auth_fail`のいずれでもない場合も`Optional.empty()`(不正遷移として扱う)。
  - `void markSecondAuthFail(String loginStatusId)` — `CURRENT_STATUS='second_auth_fail'`(`EXPIRES_AT`は変更しない)。
  - `void deleteFor(String accountId, String sessionId)` — `(ACCNT_ID, SESSION_ID)`に一致する行を物理削除。
  - `int deleteExpired()` — `EXPIRES_AT`が現在時刻より過去の行を全て物理削除し、削除件数を返す。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/service/LoginStatusServiceTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;

@ExtendWith(MockitoExtension.class)
class LoginStatusServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoginStatusService loginStatusService;

    @Test
    void 有効な行が存在しない場合はnot_auth状態で新規作成されること() {

        when(recordQueryService.select(eq("SELECT LOGIN_STATUS_ID, CURRENT_STATUS, PASSCODE_HASH, EXPIRES_AT "
                + "FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?"), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(), new ArrayList<>(List.of(insertedRow())));

        LinkedHashMap<String, String> result = loginStatusService.beginAttempt("1000001", "session-1");

        assertThat(result.get("CURRENT_STATUS")).isEqualTo("not_auth");
    }

    private LinkedHashMap<String, String> insertedRow() {
        LinkedHashMap<String, String> insertedRow = new LinkedHashMap<>();
        insertedRow.put("LOGIN_STATUS_ID", "1");
        insertedRow.put("CURRENT_STATUS", "not_auth");
        return insertedRow;
    }

    @Test
    void 検証時にステータスがfirst_auth_passでもsecond_auth_failでもない場合は空を返すこと() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "not_auth");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isEmpty();
    }

    @Test
    void 検証時にsecond_auth_fail状態は有効な検証対象として返されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "second_auth_fail");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("CURRENT_STATUS")).isEqualTo("second_auth_fail");
    }

    @Test
    void 期限切れの行は物理削除された上で空を返すこと() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "first_auth_pass");
        row.put("EXPIRES_AT", "2000-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isEmpty();
        verify(jdbcTemplate).update(eq("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?"), eq("1"));
    }

    @Test
    void 期限切れ行の一括削除は削除件数を返すこと() {

        when(jdbcTemplate.update(any(String.class), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(3);

        int deletedCount = loginStatusService.deleteExpired();

        assertThat(deletedCount).isEqualTo(3);
    }
}
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.LoginStatusServiceTest"`
Expected: FAIL(`LoginStatusService`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 3: LoginStatusServiceを実装する**

`src/main/java/com/freedom/taskall_v2/web/service/LoginStatusService.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;

/**
 * 「ログイン試行(LOGIN_STATUS)」テーブルの読み書きを行うサービスです。
 *
 * <p>
 * 1回のログイン試行(=1ブラウザセッション)の状態を{@code (ACCNT_ID, SESSION_ID)}単位で
 * 管理します。アカウント単位の失敗回数・ロック状態は{@link AccntAuthLockService}が別途
 * 管理するため、本クラスでは扱いません。
 * </p>
 */
@Service
public class LoginStatusService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIND_SQL =
            "SELECT LOGIN_STATUS_ID, CURRENT_STATUS, PASSCODE_HASH, EXPIRES_AT "
                    + "FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public LoginStatusService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * このセッションでのログイン試行を開始します。有効な行が無ければ新規作成し、
     * 有効期限内の行が既に存在すればそのまま返却します。
     */
    public LinkedHashMap<String, String> beginAttempt(String accountId, String sessionId) {

        Optional<LinkedHashMap<String, String>> existing = findRow(accountId, sessionId);
        if (existing.isPresent() && !isExpired(existing.get())) {
            return existing.get();
        }
        existing.ifPresent(row -> jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?",
                row.get("LOGIN_STATUS_ID")));

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        String expiresAt = LocalDateTime.now().plusMinutes(30).format(DATE_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO LOGIN_STATUS
                    (ACCNT_ID, SESSION_ID, CURRENT_STATUS, EXPIRES_AT, VERSION, IS_DELETED,
                     CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                VALUES (?, ?, 'not_auth', ?, 1, 0, ?, ?, ?, ?)
                """, accountId, sessionId, expiresAt, accountId, currentDate, accountId, currentDate);

        return findRow(accountId, sessionId)
                .orElseThrow(() -> new IllegalStateException("LOGIN_STATUS行の作成直後の再取得に失敗しました。"));
    }

    /** 一次認証通過時の状態へ更新します(有効期限を5分後へ短縮し、パスコードハッシュを保存する)。 */
    public void markFirstAuthPass(String loginStatusId, String passcodeHash) {
        String expiresAt = LocalDateTime.now().plusMinutes(5).format(DATE_FORMAT);
        updateStatus(loginStatusId, "first_auth_pass", passcodeHash, expiresAt);
    }

    /** 一次認証失敗時の状態へ更新します(有効期限は変更しません)。 */
    public void markFirstAuthFail(String loginStatusId) {
        jdbcTemplate.update("UPDATE LOGIN_STATUS SET CURRENT_STATUS = 'first_auth_fail' WHERE LOGIN_STATUS_ID = ?",
                loginStatusId);
    }

    /**
     * 二次認証の照合対象として、有効な行を検索します。行が無い・期限切れ・不正な遷移
     * (現在ステータスがfirst_auth_pass/second_auth_fail以外)のいずれかの場合は空を返します。
     * 期限切れの場合は行を物理削除した上で空を返します。
     */
    public Optional<LinkedHashMap<String, String>> findForVerification(String accountId, String sessionId) {

        Optional<LinkedHashMap<String, String>> row = findRow(accountId, sessionId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        if (isExpired(row.get())) {
            jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?", row.get().get("LOGIN_STATUS_ID"));
            return Optional.empty();
        }

        // 二次認証の再入力待機中(second_auth_fail)も、一次認証通過直後(first_auth_pass)と
        // 同様に有効な検証対象として扱う(一度誤入力しただけで再入力できなくなる不具合を防ぐ)
        String currentStatus = row.get().get("CURRENT_STATUS");
        if (!"first_auth_pass".equals(currentStatus) && !"second_auth_fail".equals(currentStatus)) {
            return Optional.empty();
        }

        return row;
    }

    /** 二次認証失敗時の状態へ更新します(有効期限は変更しません)。 */
    public void markSecondAuthFail(String loginStatusId) {
        jdbcTemplate.update("UPDATE LOGIN_STATUS SET CURRENT_STATUS = 'second_auth_fail' WHERE LOGIN_STATUS_ID = ?",
                loginStatusId);
    }

    /** 役目を終えた行(二次認証成功時)を物理削除します。 */
    public void deleteFor(String accountId, String sessionId) {
        jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?", accountId, sessionId);
    }

    /** 有効期限切れの行を一括で物理削除し、削除件数を返します(定期クリーンアップ処理から呼び出す)。 */
    public int deleteExpired() {
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        return jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE EXPIRES_AT < ?", currentDate);
    }

    private Optional<LinkedHashMap<String, String>> findRow(String accountId, String sessionId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_SQL, List.of(accountId, sessionId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private boolean isExpired(LinkedHashMap<String, String> row) {
        LocalDateTime expiresAt = java.time.LocalDateTime.parse(row.get("EXPIRES_AT"), DATE_FORMAT);
        return expiresAt.isBefore(LocalDateTime.now());
    }

    private void updateStatus(String loginStatusId, String status, String passcodeHash, String expiresAt) {
        jdbcTemplate.update(
                "UPDATE LOGIN_STATUS SET CURRENT_STATUS = ?, PASSCODE_HASH = ?, EXPIRES_AT = ? WHERE LOGIN_STATUS_ID = ?",
                status, passcodeHash, expiresAt, loginStatusId);
    }
}
```

注意: `deleteExpired()`のSQLは本来バインドパラメータ化すべきだが、`RecordQueryService`はSELECT専用でUPDATE/DELETEのバインド実行手段が無いため、`JdbcTemplate.update(String sql, Object... args)`のオーバーロードを使い`jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE EXPIRES_AT < ?", currentDate)`という形に実装時点で修正すること(文字列連結は避ける。上記コード中の文字列連結は本文書作成時点の記述漏れであり、実装時はバインドパラメータ形式に直す)。

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.LoginStatusServiceTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/service/LoginStatusService.java src/test/java/com/freedom/taskall_v2/web/service/LoginStatusServiceTest.java
git commit -m "feat: LOGIN_STATUSテーブルを操作するLoginStatusServiceを追加"
```

---

## Task 5: AccntAuthLockServiceを実装する(ACCNT_AUTH_LOCKテーブルの読み書き)

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/service/AccntAuthLockService.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/service/AccntAuthLockServiceTest.java`

**Interfaces:**
- Consumes: `RecordQueryService`/`JdbcTemplate`(Task 4と同様のDIパターン)。
- Produces: 以降のTask 7・Task 9が使用するメソッド群:
  - `boolean isLocked(String accountId)` — `LOCKED_UNTIL`が現在時刻より未来なら`true`。行が無い、または`LOCKED_UNTIL`が現在時刻以前(自然失効済み)なら`false`。**自然失効を検知した場合は`FAIL_CNT`を0へリセットする**(設計書に明記の無い補完。失効後も失敗回数が残存すると次の1回の誤入力で即再ロックされてしまう論理的な欠陥を避けるため。`msg.warn.web.twoFactor.accountLockExpired`でWARNログを出力する)。
  - `void recordFailure(String accountId)` — 行が無ければ`FAIL_CNT=1`で新規作成。存在すれば`FAIL_CNT`を1増やす。増加後の`FAIL_CNT`が5に達したら`LOCKED_UNTIL`を現在時刻+15分に設定する。
  - `void resetFailCountOnSuccess(String accountId)` — 行が存在すれば`FAIL_CNT=0`、`LOCKED_UNTIL=NULL`に更新する(行が無ければ何もしない)。
  - `void deleteForAccount(String accountId)` — 行を物理削除する(行が無くても何もしない)。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/service/AccntAuthLockServiceTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;

@ExtendWith(MockitoExtension.class)
class AccntAuthLockServiceTest {

    private static final String FIND_SQL = "SELECT ACCNT_AUTH_LOCK_ID, FAIL_CNT, LOCKED_UNTIL "
            + "FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AccntAuthLockService accntAuthLockService;

    @Test
    void 行が存在しない場合はロックされていないと判定されること() {

        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        assertThat(accntAuthLockService.isLocked("1000001")).isFalse();
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void ロック解除予定時刻が未来の場合はロック中と判定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "5");
        row.put("LOCKED_UNTIL", "2999-01-01 00:00:00");
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        assertThat(accntAuthLockService.isLocked("1000001")).isTrue();
    }

    @Test
    void ロック解除予定時刻が過去の場合はロックされていないと判定され失敗回数がリセットされること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "5");
        row.put("LOCKED_UNTIL", "2000-01-01 00:00:00");
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        assertThat(accntAuthLockService.isLocked("1000001")).isFalse();
        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?"),
                eq("1"));
    }

    @Test
    void 失敗回数が5に達するとロック解除予定時刻が設定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "4");
        row.put("LOCKED_UNTIL", null);
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        accntAuthLockService.recordFailure("1000001");

        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = ?, LOCKED_UNTIL = ? WHERE ACCNT_AUTH_LOCK_ID = ?"),
                eq(5), any(String.class), eq("1"));
    }

    @Test
    void 行が存在しない場合の失敗記録は新規作成のINSERT文が実行されること() {

        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        accntAuthLockService.recordFailure("1000001");

        verify(jdbcTemplate, times(1)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO ACCNT_AUTH_LOCK"),
                eq("1000001"), eq("1000001"), any(String.class), eq("1000001"), any(String.class));
    }
}
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.AccntAuthLockServiceTest"`
Expected: FAIL(`AccntAuthLockService`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 3: AccntAuthLockServiceを実装する**

`src/main/java/com/freedom/taskall_v2/web/service/AccntAuthLockService.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 「アカウント認証ロック(ACCNT_AUTH_LOCK)」テーブルの読み書きを行うサービスです。
 *
 * <p>
 * アカウント単位(セッションをまたいだ合算)で認証失敗回数とロック状態を管理します。
 * どのセッションからの失敗であっても、この1行に対して合算することでブルートフォース対策の
 * 集計をアカウント全体で行います。
 * </p>
 */
@Service
public class AccntAuthLockService {

    private static final Logger logger = LoggerFactory.getLogger(AccntAuthLockService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MAX_FAIL_COUNT = 5;

    private static final String FIND_SQL = "SELECT ACCNT_AUTH_LOCK_ID, FAIL_CNT, LOCKED_UNTIL "
            + "FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final MsgUtil msg;

    public AccntAuthLockService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
        this.msg = msg;
    }

    /**
     * アカウントがロック中かどうかを判定します。ロック解除予定時刻が現在時刻より未来の場合のみ
     * ロック中とします。自然失効(過去のロック解除予定時刻が残存している状態)を検知した場合は、
     * 次回の誤入力で即座に再ロックされることを防ぐため、失敗回数を0へリセットします。
     */
    public boolean isLocked(String accountId) {

        Optional<LinkedHashMap<String, String>> row = findRow(accountId);
        if (row.isEmpty()) {
            return false;
        }

        String lockedUntil = row.get().get("LOCKED_UNTIL");
        if (lockedUntil == null || LocalDateTime.parse(lockedUntil, DATE_FORMAT).isBefore(LocalDateTime.now())) {
            if (lockedUntil != null) {
                logger.warn(msg.get("msg.warn.web.twoFactor.accountLockExpired", accountId));
                jdbcTemplate.update(
                        "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?",
                        row.get().get("ACCNT_AUTH_LOCK_ID"));
            }
            return false;
        }
        return true;
    }

    /** 認証失敗を記録します。失敗回数が上限に達した場合はロック解除予定時刻を設定します。 */
    public void recordFailure(String accountId) {

        Optional<LinkedHashMap<String, String>> row = findRow(accountId);
        if (row.isEmpty()) {
            String currentDate = LocalDateTime.now().format(DATE_FORMAT);
            jdbcTemplate.update("""
                    INSERT INTO ACCNT_AUTH_LOCK
                        (ACCNT_ID, FAIL_CNT, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                    VALUES (?, 1, 1, 0, ?, ?, ?, ?)
                    """, accountId, accountId, currentDate, accountId, currentDate);
            return;
        }

        int failCount = Integer.parseInt(row.get().get("FAIL_CNT")) + 1;
        if (failCount >= MAX_FAIL_COUNT) {
            String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
            jdbcTemplate.update(
                    "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = ?, LOCKED_UNTIL = ? WHERE ACCNT_AUTH_LOCK_ID = ?",
                    failCount, lockedUntil, row.get().get("ACCNT_AUTH_LOCK_ID"));
        } else {
            jdbcTemplate.update("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = ? WHERE ACCNT_AUTH_LOCK_ID = ?",
                    failCount, row.get().get("ACCNT_AUTH_LOCK_ID"));
        }
    }

    /** 認証成功時に失敗回数・ロック状態をクリアします(行が無ければ何もしません)。 */
    public void resetFailCountOnSuccess(String accountId) {
        findRow(accountId).ifPresent(row -> jdbcTemplate.update(
                "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?",
                row.get("ACCNT_AUTH_LOCK_ID")));
    }

    /** ログイン成功により不要となった行を物理削除します(行が無くても何もしません)。 */
    public void deleteForAccount(String accountId) {
        jdbcTemplate.update("DELETE FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?", accountId);
    }

    private Optional<LinkedHashMap<String, String>> findRow(String accountId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_SQL, List.of(accountId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
```

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.AccntAuthLockServiceTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/service/AccntAuthLockService.java src/test/java/com/freedom/taskall_v2/web/service/AccntAuthLockServiceTest.java
git commit -m "feat: ACCNT_AUTH_LOCKテーブルを操作するAccntAuthLockServiceを追加"
```

---

## Task 6: PasscodeGenerator・TwoFactorMailServiceを実装する(6桁コード生成とメール送信)

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/util/PasscodeGenerator.java`
- Create: `src/main/java/com/freedom/taskall_v2/web/service/TwoFactorMailService.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/util/PasscodeGeneratorTest.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/service/TwoFactorMailServiceTest.java`
- Modify: `src/main/resources/application-local.yaml`
- Modify: `src/main/resources/application-prod.yaml`

**Interfaces:**
- Produces: `PasscodeGenerator.generate()`(6桁のゼロ埋め数字文字列、例: `"042817"`を返す`@Component`)。`TwoFactorMailService.sendPasscode(String mailAddress, String passcode)`(`void`、失敗時は`ApplicationInternalException`)。以降のTask 7が両クラスを使用する。

- [ ] **Step 1: PasscodeGeneratorの失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/util/PasscodeGeneratorTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class PasscodeGeneratorTest {

    private final PasscodeGenerator passcodeGenerator = new PasscodeGenerator();

    @RepeatedTest(20)
    void 生成される値は必ず6桁の数字文字列であること() {

        String passcode = passcodeGenerator.generate();

        assertThat(passcode).hasSize(6);
        assertThat(passcode).matches("[0-9]{6}");
    }

    @Test
    void ゼロ埋めされた小さい値も6桁で返却されること() {

        // SecureRandomの結果に依存せず、ゼロ埋め処理自体を検証するため複数回試行して0始まりの値が
        // 出現することを確認する(1000000通り中0〜99999が出現する確率は約10%であり、20回中に
        // 十分な回数出現することを期待する)
        boolean foundZeroPadded = false;
        for (int i = 0; i < 200; i++) {
            if (passcodeGenerator.generate().startsWith("0")) {
                foundZeroPadded = true;
                break;
            }
        }
        assertThat(foundZeroPadded).isTrue();
    }
}
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.util.PasscodeGeneratorTest"`
Expected: FAIL(`PasscodeGenerator`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 3: PasscodeGeneratorを実装する**

`src/main/java/com/freedom/taskall_v2/web/util/PasscodeGenerator.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 二段階認証で使用する6桁のランダムなパスコードを生成するユーティリティです。
 *
 * <p>
 * 推測されにくい乱数を生成するため{@link SecureRandom}を使用します。静的utilではなく
 * {@code @Component}とするのは、テスト時にモック化してパスコードを固定できるようにするためです。
 * </p>
 */
@Component
public class PasscodeGenerator {

    private static final int PASSCODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 000000〜999999のいずれかの6桁ゼロ埋め数字文字列を生成します。
     *
     * @return 6桁のパスコード文字列
     */
    public String generate() {
        int value = secureRandom.nextInt(PASSCODE_BOUND);
        return String.format("%06d", value);
    }
}
```

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.util.PasscodeGeneratorTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: メール送信設定をapplication-local.yaml/application-prod.yamlへ追加する**

`src/main/resources/application-local.yaml`の`spring:`ブロックへ、`datasource:`の次に追記する:

```yaml
  # メール送信設定(ローカル開発ではMailHog等のダミーSMTPサーバを想定)
  mail:
    host: localhost
    port: 1025
    username:
    password:
```

`src/main/resources/application-prod.yaml`の`spring:`ブロックへ、`datasource:`の次に追記する(既存の`TASKALL_DATASOURCE_URL`と同じ、環境変数によるオーバーライドのパターンを踏襲する):

```yaml
  # メール送信設定(本番はSMTPサーバの接続情報を環境変数で注入する)
  mail:
    host: ${TASKALL_MAIL_HOST}
    port: ${TASKALL_MAIL_PORT:587}
    username: ${TASKALL_MAIL_USERNAME}
    password: ${TASKALL_MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

- [ ] **Step 6: TwoFactorMailServiceの失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/service/TwoFactorMailServiceTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class TwoFactorMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private TwoFactorMailService twoFactorMailService;

    // MsgUtilは実ファイル(messages.properties)を読み込むため、モック化せず実インスタンスを使う
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        twoFactorMailService = new TwoFactorMailService(javaMailSender, new MsgUtil());
    }

    @Test
    void 宛先件名本文を指定してメールが送信されること() {

        twoFactorMailService.sendPasscode("user@example.com", "042817");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("user@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("二段階認証パスコード");
        assertThat(sentMessage.getText()).isEqualTo("次の6桁の数字をご入力ください。\n  042 817");
    }

    @Test
    void 送信に失敗した場合はApplicationInternalExceptionがスローされること() {

        doThrow(new MailSendException("smtp error")).when(javaMailSender).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> twoFactorMailService.sendPasscode("user@example.com", "042817"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
```

- [ ] **Step 7: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.TwoFactorMailServiceTest"`
Expected: FAIL(`TwoFactorMailService`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 8: TwoFactorMailServiceを実装する**

`src/main/java/com/freedom/taskall_v2/web/service/TwoFactorMailService.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 二段階認証用の6桁パスコードをメールで送信するサービスです。
 *
 * <p>
 * タイトルは「二段階認証パスコード」、本文は「次の6桁の数字をご入力ください。」の次の行に
 * 半角スペース2文字＋3桁＋半角スペース＋3桁の書式(例: {@code "  042 817"})でパスコードを
 * 記載する(設計書「二段階認証の処理概略」節)。
 * </p>
 */
@Service
public class TwoFactorMailService {

    private static final String MAIL_SUBJECT = "二段階認証パスコード";

    private final JavaMailSender javaMailSender;
    private final MsgUtil msg;

    public TwoFactorMailService(JavaMailSender javaMailSender, MsgUtil msg) {
        this.javaMailSender = javaMailSender;
        this.msg = msg;
    }

    /**
     * 指定したメールアドレスへ、6桁のパスコードを記載したメールを送信します。
     *
     * @param mailAddress 送信先メールアドレス
     * @param passcode    6桁のパスコード文字列(例: {@code "042817"})
     * @throws ApplicationInternalException メール送信に失敗した場合
     */
    public void sendPasscode(String mailAddress, String passcode) {

        String formattedPasscode = passcode.substring(0, 3) + " " + passcode.substring(3);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(mailAddress);
        message.setSubject(MAIL_SUBJECT);
        message.setText("次の6桁の数字をご入力ください。\n  " + formattedPasscode);

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.twoFactor.mailSendFailed", mailAddress), e);
        }
    }
}
```

- [ ] **Step 9: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.TwoFactorMailServiceTest" --tests "com.freedom.taskall_v2.web.util.PasscodeGeneratorTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/util/PasscodeGenerator.java src/main/java/com/freedom/taskall_v2/web/service/TwoFactorMailService.java src/test/java/com/freedom/taskall_v2/web/util/PasscodeGeneratorTest.java src/test/java/com/freedom/taskall_v2/web/service/TwoFactorMailServiceTest.java src/main/resources/application-local.yaml src/main/resources/application-prod.yaml
git commit -m "feat: 二段階認証用のパスコード生成・メール送信を追加"
```

---

## Task 7: TwoFactorRequiredException・TwoPhaseAuthenticationProviderを実装し、SecurityConfigへ登録する

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/security/TwoFactorRequiredException.java`
- Create: `src/main/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProvider.java`
- Modify: `src/main/java/com/freedom/taskall_v2/web/security/SecurityConfig.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProviderTest.java`

**Interfaces:**
- Consumes: `AccountUserDetailsService.loadUserByUsername(String)`(Task済み既存クラス、返り値`AccountPrincipal`)、`PasswordEncoder`(既存Beanの`BCryptPasswordEncoder`、`matches`/`encode`両方を使用)、`AccntAuthLockService.isLocked/recordFailure/resetFailCountOnSuccess`(Task 5)、`LoginStatusService.beginAttempt/markFirstAuthPass/markFirstAuthFail`(Task 4)、`PasscodeGenerator.generate()`/`TwoFactorMailService.sendPasscode`(Task 6)。
- Produces: `TwoFactorRequiredException`(一次認証通過・二次認証待ちであることを表す`AuthenticationException`)。以降のTask 8(`AccountAuthenticationFailureHandler`)がこの例外型と`org.springframework.security.authentication.LockedException`を判別して分岐する。セッション属性`pendingTwoFactorAccountId`(以降のTask 9・Task 10が参照)。

- [ ] **Step 1: TwoFactorRequiredExceptionを実装する**

`src/main/java/com/freedom/taskall_v2/web/security/TwoFactorRequiredException.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.security;

import org.springframework.security.core.AuthenticationException;

/**
 * 一次認証(メールアドレス・パスワード)を通過し、二次認証(6桁パスコード)待ちであることを表す例外です。
 *
 * <p>
 * SpringSecurityの認証フローでは、一次認証を通過してもまだ「ログイン成功」として扱わないために
 * (二段階認証を必須とするため)、通常の認証成功ではなく、この専用の例外をスローして
 * {@code AuthenticationFailureHandler}側に処理を委ねます。
 * </p>
 */
public class TwoFactorRequiredException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public TwoFactorRequiredException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: TwoPhaseAuthenticationProviderの失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProviderTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.web.service.AccntAuthLockService;
import com.freedom.taskall_v2.web.service.LoginStatusService;
import com.freedom.taskall_v2.web.service.TwoFactorMailService;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;

@ExtendWith(MockitoExtension.class)
class TwoPhaseAuthenticationProviderTest {

    @Mock
    private AccountUserDetailsService accountUserDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccntAuthLockService accntAuthLockService;

    @Mock
    private LoginStatusService loginStatusService;

    @Mock
    private PasscodeGenerator passcodeGenerator;

    @Mock
    private TwoFactorMailService twoFactorMailService;

    private MockHttpServletRequest request;

    private TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.getSession(true);
        twoPhaseAuthenticationProvider = new TwoPhaseAuthenticationProvider(accountUserDetailsService,
                passwordEncoder, accntAuthLockService, loginStatusService, passcodeGenerator, twoFactorMailService,
                request);
    }

    @Test
    void メールアドレスに対応するアカウントが存在しない場合は他のテーブル操作なしでBadCredentialsExceptionとなること() {

        when(accountUserDetailsService.loadUserByUsername("nobody@example.com"))
                .thenThrow(new UsernameNotFoundException("nobody@example.com"));
        Authentication authentication = new UsernamePasswordAuthenticationToken("nobody@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(BadCredentialsException.class);

        verify(accntAuthLockService, org.mockito.Mockito.never()).isLocked(any());
        verify(loginStatusService, org.mockito.Mockito.never()).beginAttempt(any(), any());
    }

    @Test
    void ロック中のアカウントはLockedExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(true);
        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void パスワードが一致する場合はパスコードメールを送信しTwoFactorRequiredExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> loginStatusRow = new LinkedHashMap<>();
        loginStatusRow.put("LOGIN_STATUS_ID", "9");
        when(loginStatusService.beginAttempt("1000001", request.getSession().getId())).thenReturn(loginStatusRow);

        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("042817")).thenReturn("hashed-042817");

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(TwoFactorRequiredException.class);

        verify(loginStatusService).markFirstAuthPass("9", "hashed-042817");
        verify(accntAuthLockService).resetFailCountOnSuccess("1000001");
        verify(twoFactorMailService).sendPasscode("user@example.com", "042817");
        assertThat(request.getSession().getAttribute("pendingTwoFactorAccountId")).isEqualTo("1000001");
    }

    @Test
    void パスワードが一致しない場合は失敗が記録されBadCredentialsExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> loginStatusRow = new LinkedHashMap<>();
        loginStatusRow.put("LOGIN_STATUS_ID", "9");
        when(loginStatusService.beginAttempt(eq("1000001"), any())).thenReturn(loginStatusRow);
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "wrong-password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginStatusService).markFirstAuthFail("9");
        verify(accntAuthLockService).recordFailure("1000001");
    }
}
```

- [ ] **Step 3: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.security.TwoPhaseAuthenticationProviderTest"`
Expected: FAIL(`TwoPhaseAuthenticationProvider`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 4: TwoPhaseAuthenticationProviderを実装する**

`src/main/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProvider.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.security;

import java.util.LinkedHashMap;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.web.service.AccntAuthLockService;
import com.freedom.taskall_v2.web.service.LoginStatusService;
import com.freedom.taskall_v2.web.service.TwoFactorMailService;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;

import jakarta.servlet.http.HttpServletRequest;

/**
 * メールアドレス・パスワードによる一次認証を行い、通過した場合は二次認証(6桁パスコード)を
 * 要求する{@link AuthenticationProvider}です。
 *
 * <p>
 * 設計書(documents/design/2000006_two_phase_login.md)「二段階認証処理」節の一次認証手順
 * (アカウント検索→アカウント認証ロック確認→ログイン試行登録→パスワード照合→
 * 成功/失敗時のテーブル更新)をそのまま実装します。一次認証に成功しても、ここでは
 * SpringSecurityの認証成功として扱わず{@link TwoFactorRequiredException}をスローし、
 * 実際のログイン確立は二次認証成功時({@code VerifyTwoFactorAuthService})まで持ち越します。
 * </p>
 */
@Component
public class TwoPhaseAuthenticationProvider implements AuthenticationProvider {

    private final AccountUserDetailsService accountUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AccntAuthLockService accntAuthLockService;
    private final LoginStatusService loginStatusService;
    private final PasscodeGenerator passcodeGenerator;
    private final TwoFactorMailService twoFactorMailService;
    private final HttpServletRequest request;

    public TwoPhaseAuthenticationProvider(AccountUserDetailsService accountUserDetailsService,
            PasswordEncoder passwordEncoder, AccntAuthLockService accntAuthLockService,
            LoginStatusService loginStatusService, PasscodeGenerator passcodeGenerator,
            TwoFactorMailService twoFactorMailService, HttpServletRequest request) {
        this.accountUserDetailsService = accountUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.accntAuthLockService = accntAuthLockService;
        this.loginStatusService = loginStatusService;
        this.passcodeGenerator = passcodeGenerator;
        this.twoFactorMailService = twoFactorMailService;
        this.request = request;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String mailAddress = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();

        // メールアドレスに対応するアカウントが存在しない場合は、以降のテーブル操作を一切行わず
        // 即座に認証失敗とする(アカウント存在有無を推測されないようにするため)
        AccountPrincipal principal;
        try {
            principal = (AccountPrincipal) accountUserDetailsService.loadUserByUsername(mailAddress);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("メールアドレスもしくはパスワードが間違っています。");
        }
        String accountId = principal.getAccountId();

        // アカウント全体がロック中の場合は、このセッションが原因かどうかに関わらず処理を打ち切る
        if (accntAuthLockService.isLocked(accountId)) {
            throw new LockedException("アカウントは現在、ロックされています。");
        }

        // このセッション専用のログイン試行行を用意する(有効期限内の既存行があれば引き継ぐ)
        String sessionId = request.getSession().getId();
        LinkedHashMap<String, String> loginStatus = loginStatusService.beginAttempt(accountId, sessionId);
        String loginStatusId = loginStatus.get("LOGIN_STATUS_ID");

        if (passwordEncoder.matches(rawPassword, principal.getPassword())) {
            return handlePasswordSuccess(accountId, mailAddress, loginStatusId);
        }
        return handlePasswordFailure(accountId, loginStatusId);
    }

    private Authentication handlePasswordSuccess(String accountId, String mailAddress, String loginStatusId) {

        // 6桁のパスコードを生成し、一方向ハッシュ化した値のみをLOGIN_STATUSへ保存する
        // (平文の6桁数字はDBに保存しない)
        String passcode = passcodeGenerator.generate();
        String passcodeHash = passwordEncoder.encode(passcode);
        loginStatusService.markFirstAuthPass(loginStatusId, passcodeHash);
        accntAuthLockService.resetFailCountOnSuccess(accountId);
        twoFactorMailService.sendPasscode(mailAddress, passcode);

        // 二次認証のPOST/GET処理が参照できるよう、セッションへ一次認証通過中のアカウントIDを記録する
        request.getSession().setAttribute("pendingTwoFactorAccountId", accountId);

        throw new TwoFactorRequiredException("一次認証を通過しました。二段階認証のパスコードを入力してください。");
    }

    private Authentication handlePasswordFailure(String accountId, String loginStatusId) {
        loginStatusService.markFirstAuthFail(loginStatusId);
        accntAuthLockService.recordFailure(accountId);
        throw new BadCredentialsException("メールアドレスもしくはパスワードが間違っています。");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

- [ ] **Step 5: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.security.TwoPhaseAuthenticationProviderTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: SecurityConfigへTwoPhaseAuthenticationProviderを明示的に登録する**

`src/main/java/com/freedom/taskall_v2/web/security/SecurityConfig.java`を以下のように変更する(コンストラクタで`TwoPhaseAuthenticationProvider`を追加注入し、`securityFilterChain`メソッド内で`.authenticationProvider(...)`を呼び出す):

```java
    private final AccountAuthenticationSuccessHandler successHandler;
    private final AccountAuthenticationFailureHandler failureHandler;
    private final TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider;

    public SecurityConfig(AccountAuthenticationSuccessHandler successHandler,
            AccountAuthenticationFailureHandler failureHandler,
            TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.twoPhaseAuthenticationProvider = twoPhaseAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 認可判定は既存のAuthUtil(HTML_PARTS_IN_APROLE)に委ねるため、SpringSecurity側では
                // 全リクエストを許可する
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // メールアドレス・パスワードの照合およびロック判定・二段階認証の起動は、
                // SpringBoot自動構成のDaoAuthenticationProviderではなく本クラス専用の
                // TwoPhaseAuthenticationProviderへ明示的に委譲する
                .authenticationProvider(twoPhaseAuthenticationProvider)
                // ログイン画面/処理は既存のマイページ(POST)のURLをそのまま流用する
                .formLogin(form -> form
                        .loginPage(LOGIN_PAGE_URL)
                        .loginProcessingUrl(LOGIN_PAGE_URL)
                        .usernameParameter("MAIL_ADDRESS")
                        .passwordParameter("PASSWORD")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                // ログアウトは専用URLを新設し、成功後はマイページへリダイレクトする
                .logout(logout -> logout
                        .logoutUrl("/taskall-v2/service/logout.html")
                        .logoutSuccessUrl(LOGIN_PAGE_URL));

        return http.build();
    }
```

- [ ] **Step 7: 全体テストを実行し既存の認証系テストに影響がないことを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/security/TwoFactorRequiredException.java src/main/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProvider.java src/main/java/com/freedom/taskall_v2/web/security/SecurityConfig.java src/test/java/com/freedom/taskall_v2/web/security/TwoPhaseAuthenticationProviderTest.java
git commit -m "feat: TwoPhaseAuthenticationProviderによる一次認証+二段階認証起動を実装"
```

---

## Task 8: AccountAuthenticationFailureHandlerを拡張し、例外種別ごとに遷移先を分岐する

**Files:**
- Modify: `src/main/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandler.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandlerTest.java`(既存テストが無ければ新規作成、既存があれば追記)

**Interfaces:**
- Consumes: `TwoFactorRequiredException`(Task 7)、`org.springframework.security.authentication.LockedException`(SpringSecurity標準)、`ErrMsgService.getErrMsgKey`(既存)。
- Produces: 例外種別ごとのリダイレクト先(`TwoFactorRequiredException`→`/taskall-v2/service/twoFactorAuth.html`、`LockedException`→アカウントロック中エラー付きの`myPage.html`、それ以外→従来通りのログインエラー付き`myPage.html`)。

- [ ] **Step 1: 既存テストの有無を確認する**

Run: `find src/test -iname "AccountAuthenticationFailureHandlerTest.java"`

既存テストが見つかった場合は、その内容を確認した上でStep 2以降のテストケースを追記する形にする(既存テストの前提・モック設定を壊さないよう注意する)。見つからない場合はStep 2の通り新規作成する。

- [ ] **Step 2: 失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandlerTest.java`(新規、または追記):

```java
package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import com.freedom.taskall_v2.web.service.ErrMsgService;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationFailureHandlerTest {

    @Mock
    private ErrMsgService errMsgService;

    @InjectMocks
    private AccountAuthenticationFailureHandler accountAuthenticationFailureHandler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.getSession(true);
        response = new MockHttpServletResponse();
    }

    @Test
    void 通常の認証失敗はログインエラー付きでマイページへリダイレクトされること() throws Exception {

        when(errMsgService.getErrMsgKey("session-id", "1000001", "1000401")).thenReturn("111");
        request.getSession().setId("session-id");

        accountAuthenticationFailureHandler.onAuthenticationFailure(request, response,
                new BadCredentialsException("メールアドレスもしくはパスワードが間違っています。"));

        assertThat(response.getRedirectedUrl()).isEqualTo("myPage.html?errMsgKey=111");
    }

    @Test
    void ロック中の認証失敗はアカウントロックエラー付きでマイページへリダイレクトされること() throws Exception {

        when(errMsgService.getErrMsgKey(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                eq("1000402"))).thenReturn("222");

        accountAuthenticationFailureHandler.onAuthenticationFailure(request, response,
                new LockedException("アカウントは現在、ロックされています。"));

        assertThat(response.getRedirectedUrl()).isEqualTo("myPage.html?errMsgKey=222");
    }

    @Test
    void 二段階認証待ちの例外は二段階認証画面へエラーメッセージ無しでリダイレクトされること() throws Exception {

        accountAuthenticationFailureHandler.onAuthenticationFailure(request, response,
                new TwoFactorRequiredException("一次認証を通過しました。"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/taskall-v2/service/twoFactorAuth.html");
        verify(errMsgService, never()).getErrMsgKey(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 3: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.security.AccountAuthenticationFailureHandlerTest"`
Expected: FAIL(2件目・3件目のテストが、現状の実装では常にloginErrorのメッセージキーでリダイレクトしてしまうため失敗する)

- [ ] **Step 4: AccountAuthenticationFailureHandlerを例外種別ごとの分岐に変更する**

`src/main/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandler.java`を以下の内容へ変更する:

```java
package com.freedom.taskall_v2.web.security;

import java.io.IOException;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.web.service.ErrMsgService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン失敗時に、PRG(Post/Redirect/Get)パターンで例外種別に応じた遷移先へ
 * リダイレクトする{@link AuthenticationFailureHandler}です。
 *
 * <p>
 * 旧{@code LoginService}の認証失敗時の挙動(汎用キー値マスタ{@code 1000401}からエラーメッセージを
 * 取得し{@code ERR_MSG}へ登録した上で、そのキーをクエリパラメータへ付与してリダイレクトする)を
 * 通常の認証失敗({@link org.springframework.security.authentication.BadCredentialsException}等)
 * には踏襲しつつ、{@link LockedException}(アカウントロック中)は専用のエラーメッセージへ、
 * {@link TwoFactorRequiredException}(一次認証通過・二次認証待ち)はエラーメッセージ無しで
 * 二段階認証画面へ、それぞれ分岐してリダイレクトします。
 * </p>
 */
@Component
public class AccountAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** ログイン失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String LOGIN_ERROR_GNR_KEY_VAL_ID = "1000401";

    /** アカウントロック中エラーメッセージに対応する汎用キー値マスタID */
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";

    /** 二段階認証画面のURI */
    private static final String TWO_FACTOR_AUTH_URI = "/taskall-v2/service/twoFactorAuth.html";

    /** アカウント未特定時(未ログイン)に用いるゲストアカウントのID */
    private static final String GUEST_ACCOUNT_ID = "1000001";

    private final ErrMsgService errMsgService;

    public AccountAuthenticationFailureHandler(ErrMsgService errMsgService) {
        this.errMsgService = errMsgService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        // 二次認証待ちの場合は、まだ認証エラーではないためエラーメッセージを発行せず
        // 二段階認証画面へそのまま遷移させる
        if (exception instanceof TwoFactorRequiredException) {
            response.sendRedirect(TWO_FACTOR_AUTH_URI);
            return;
        }

        String sessionId = request.getSession().getId();
        Object accountIdAttribute = request.getSession().getAttribute("accountId");
        String accountId = accountIdAttribute != null ? accountIdAttribute.toString() : GUEST_ACCOUNT_ID;

        // ロック中は専用のエラーメッセージ、それ以外(パスワード不一致等)は従来通りのログインエラーとする
        String gnrKeyValId =
                exception instanceof LockedException ? ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID : LOGIN_ERROR_GNR_KEY_VAL_ID;
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, gnrKeyValId);

        response.sendRedirect("myPage.html?errMsgKey=" + errMsgKey);
    }
}
```

- [ ] **Step 5: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.security.AccountAuthenticationFailureHandlerTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandler.java src/test/java/com/freedom/taskall_v2/web/security/AccountAuthenticationFailureHandlerTest.java
git commit -m "feat: AccountAuthenticationFailureHandlerで例外種別ごとの遷移先分岐を追加"
```

---

## Task 9: VerifyTwoFactorAuthServiceを実装する(二次認証6桁コードの照合)

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthService.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthServiceTest.java`

**Interfaces:**
- Consumes: 入力コンテキストJSONのキー`sessionId`(既存、`TaskallV2Controller#buildContext`が設定)・`pendingTwoFactorAccountId`(Task 10で`buildContext`に追加)・`TWO_FACTOR_CODE`(画面の入力フィールド名、リクエストパラメータとしてそのままコンテキストへ転記される)。`AccntAuthLockService`(Task 5)・`LoginStatusService`(Task 4)・`PasswordEncoder`(既存Bean)・`ErrMsgService`(既存)。
- Produces: 出力JSONキー`respKind`/`destination`(既存の`TaskallV2Controller#resolveViewName`が解釈するPRGパターン)、成功時のみ`account`配列(`[{"ACCNT_ID": accountId}]`、既存の`TaskallV2Controller#storeAccountIdIfExists`がこの配列を見てセッションへ`accountId`を格納する既存の仕組みをそのまま利用し、実際のログイン確立を行う)、`twoFactorAuthCompleted`(真偽値、Task 10の`TaskallV2Controller`がこのフラグを見て`pendingTwoFactorAccountId`セッション属性を削除する)。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthServiceTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VerifyTwoFactorAuthServiceTest {

    @Mock
    private LoginStatusService loginStatusService;

    @Mock
    private AccntAuthLockService accntAuthLockService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ErrMsgService errMsgService;

    @InjectMocks
    private VerifyTwoFactorAuthService verifyTwoFactorAuthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void pendingTwoFactorAccountIdが無い場合は業務エラーとなること() {

        String contextJson = "{\"sessionId\":\"session-1\"}";

        org.junit.jupiter.api.function.Executable executable =
                () -> verifyTwoFactorAuthService.execute(contextJson);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class, executable);
    }

    @Test
    public void アカウントロック中はTOP画面へアカウントロックエラー付きでリダイレクトされること() throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(true);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("222");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("respKind").asString()).isEqualTo("redirect");
        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=222");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }

    @Test
    public void LOGIN_STATUS行が検証対象として見つからない場合はTOP画面へ有効期限切れエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.empty());
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000403")).thenReturn("333");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=333");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }

    @Test
    public void パスコードが一致する場合はマイページへ遷移しaccountId配列が出力されること() throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("042817", "hashed-042817")).thenReturn(true);

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/myPage.html");
        assertThat(result.path("account").get(0).path("ACCNT_ID").asString()).isEqualTo("1000001");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
        verify(loginStatusService).deleteFor("1000001", "session-1");
        verify(accntAuthLockService).deleteForAccount("1000001");
    }

    @Test
    public void パスコードが一致せずロックに達しない場合は二段階認証画面へコードエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false, false);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("000000", "hashed-042817")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000404")).thenReturn("444");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "000000"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/twoFactorAuth.html?errMsgKey=444");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isFalse();
        verify(loginStatusService).markSecondAuthFail("9");
        verify(accntAuthLockService).recordFailure("1000001");
    }

    @Test
    public void パスコードが一致せずロックに達した場合はTOP画面へアカウントロックエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false, true);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("000000", "hashed-042817")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("555");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "000000"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=555");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }
}
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.VerifyTwoFactorAuthServiceTest"`
Expected: FAIL(`VerifyTwoFactorAuthService`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 3: VerifyTwoFactorAuthServiceを実装する**

`src/main/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthService.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 二段階認証画面から入力された6桁のパスコードを照合するサービスです。
 *
 * <p>
 * 設計書(documents/design/2000006_two_phase_login.md)「二段階認証処理」節の二次認証手順を
 * そのまま実装します。成功時は{@code account}配列を出力し、既存の
 * {@code TaskallV2Controller#storeAccountIdIfExists}の仕組みでセッションへ
 * {@code accountId}を格納することで、実際のログインを確立します。
 * </p>
 */
@Service
public class VerifyTwoFactorAuthService implements ScriptElementService {

    /** アカウントロック中エラーメッセージに対応する汎用キー値マスタID */
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";

    /** 二段階認証有効期限切れエラーメッセージに対応する汎用キー値マスタID */
    private static final String TWO_FACTOR_EXPIRED_ERROR_GNR_KEY_VAL_ID = "1000403";

    /** 二段階認証コード不一致エラーメッセージに対応する汎用キー値マスタID */
    private static final String TWO_FACTOR_CODE_ERROR_GNR_KEY_VAL_ID = "1000404";

    private final LoginStatusService loginStatusService;
    private final AccntAuthLockService accntAuthLockService;
    private final PasswordEncoder passwordEncoder;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public VerifyTwoFactorAuthService(LoginStatusService loginStatusService,
            AccntAuthLockService accntAuthLockService, PasswordEncoder passwordEncoder, ErrMsgService errMsgService,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.loginStatusService = loginStatusService;
        this.accntAuthLockService = accntAuthLockService;
        this.passwordEncoder = passwordEncoder;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = context.path("sessionId").asString("");
        String accountId = context.path("pendingTwoFactorAccountId").asString("");
        if (accountId.isBlank()) {
            throw new BusinessRuleViolationException(
                    msg.get("msg.err.web.requiredParamMissing", "pendingTwoFactorAccountId"));
        }
        String inputCode = context.path("TWO_FACTOR_CODE").asString("");
        if (inputCode.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "TWO_FACTOR_CODE"));
        }

        // アカウント全体がロック中の場合は、他セッションの失敗が原因の場合も含め一律で処理を打ち切る
        if (accntAuthLockService.isLocked(accountId)) {
            return writeAsString(buildLockedResponse(sessionId, accountId));
        }

        // このセッション向けのLOGIN_STATUS行(有効期限内・正しい遷移状態)が見つからない場合は
        // 有効期限切れとして扱う(他セッションのパスコードを使い回すケースもここに含まれる)
        Optional<LinkedHashMap<String, String>> loginStatus =
                loginStatusService.findForVerification(accountId, sessionId);
        if (loginStatus.isEmpty()) {
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId,
                    TWO_FACTOR_EXPIRED_ERROR_GNR_KEY_VAL_ID);
            return writeAsString(buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true));
        }

        String loginStatusId = loginStatus.get().get("LOGIN_STATUS_ID");
        String passcodeHash = loginStatus.get().get("PASSCODE_HASH");

        if (passwordEncoder.matches(inputCode, passcodeHash)) {
            return writeAsString(buildSuccessResponse(sessionId, accountId));
        }
        return writeAsString(buildFailureResponse(sessionId, accountId, loginStatusId));
    }

    private ObjectNode buildLockedResponse(String sessionId, String accountId) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID);
        return buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true);
    }

    private ObjectNode buildSuccessResponse(String sessionId, String accountId) {

        // 認証成功。役目を終えたLOGIN_STATUS/ACCNT_AUTH_LOCKの行はいずれも物理削除する
        loginStatusService.deleteFor(accountId, sessionId);
        accntAuthLockService.deleteForAccount(accountId);

        ObjectNode output = buildRedirectResponse("/taskall-v2/service/myPage.html", true);
        ObjectNode accountRow = objectMapper.createObjectNode();
        accountRow.put("ACCNT_ID", accountId);
        output.putArray("account").add(accountRow);
        return output;
    }

    private ObjectNode buildFailureResponse(String sessionId, String accountId, String loginStatusId) {

        // 二次認証失敗を記録し、アカウント全体の失敗回数へ合算する
        loginStatusService.markSecondAuthFail(loginStatusId);
        accntAuthLockService.recordFailure(accountId);

        if (accntAuthLockService.isLocked(accountId)) {
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID);
            return buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true);
        }

        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, TWO_FACTOR_CODE_ERROR_GNR_KEY_VAL_ID);
        return buildRedirectResponse("/taskall-v2/service/twoFactorAuth.html?errMsgKey=" + errMsgKey, false);
    }

    private ObjectNode buildRedirectResponse(String destination, boolean twoFactorAuthCompleted) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", destination);
        output.put("twoFactorAuthCompleted", twoFactorAuthCompleted);
        return output;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
```

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.VerifyTwoFactorAuthServiceTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthService.java src/test/java/com/freedom/taskall_v2/web/service/VerifyTwoFactorAuthServiceTest.java
git commit -m "feat: 二段階認証コード照合を行うVerifyTwoFactorAuthServiceを追加"
```

---

## Task 10: TaskallV2Controllerに二段階認証画面のマッピングとpendingTwoFactorAccountId連携を追加する

**Files:**
- Modify: `src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/controller/TaskallV2ControllerTest.java`(既存テストが無ければ新規作成、既存があれば追記)

**Interfaces:**
- Consumes: セッション属性`pendingTwoFactorAccountId`(Task 7の`TwoPhaseAuthenticationProvider`が設定)。出力JSONキー`twoFactorAuthCompleted`(Task 9の`VerifyTwoFactorAuthService`が設定)。
- Produces: `GET`/`POST` `/taskall-v2/service/twoFactorAuth.html`マッピング。入力コンテキストJSONキー`pendingTwoFactorAccountId`(Task 9の`VerifyTwoFactorAuthService`が参照)。ThymeleafのModel属性`pendingTwoFactorAccountId`(Task 13のテンプレートが画面表示条件として参照)。

- [ ] **Step 1: 既存テストの有無を確認する**

Run: `find src/test -iname "TaskallV2ControllerTest.java"`

既存テストが見つかった場合は内容を確認し、Step 2のテストケースを追記する形にする。見つからない場合は新規作成する。

- [ ] **Step 2: 失敗するテストを書く**

`TaskallV2ControllerTest`(新規、または追記)に以下のテストケースを追加する。既存テストが無い場合の全体は次の通り:

```java
package com.freedom.taskall_v2.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TaskallV2ControllerTest {

    @Mock
    private RequestHandlingService requestHandlingService;

    @InjectMocks
    private TaskallV2Controller taskallV2Controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void セッションにpendingTwoFactorAccountIdがある場合は入力コンテキストへ転記されModelへも設定されること() {

        taskallV2Controller = new TaskallV2Controller(requestHandlingService, objectMapper, new MsgUtil());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/taskall-v2/service/twoFactorAuth.html");
        request.getSession(true).setAttribute("pendingTwoFactorAccountId", "1000001");
        Model model = new ExtendedModelMap();

        when(requestHandlingService.execute(any())).thenAnswer(invocation -> {
            String inputJson = invocation.getArgument(0);
            assertThat(inputJson).contains("\"pendingTwoFactorAccountId\":\"1000001\"");
            return "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\"}";
        });

        taskallV2Controller.getTwoFactorAuth(request, model);

        assertThat(model.getAttribute("pendingTwoFactorAccountId")).isEqualTo("1000001");
    }

    @Test
    void twoFactorAuthCompletedがtrueの場合はpendingTwoFactorAccountIdセッション属性が削除されること() {

        taskallV2Controller = new TaskallV2Controller(requestHandlingService, objectMapper, new MsgUtil());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/taskall-v2/service/twoFactorAuth.html");
        request.getSession(true).setAttribute("pendingTwoFactorAccountId", "1000001");
        Model model = new ExtendedModelMap();

        when(requestHandlingService.execute(any()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"/taskall-v2/service/myPage.html\","
                        + "\"twoFactorAuthCompleted\":true,\"account\":[{\"ACCNT_ID\":\"1000001\"}]}");

        taskallV2Controller.postTwoFactorAuth(request, model);

        assertThat(request.getSession(false).getAttribute("pendingTwoFactorAccountId")).isNull();
    }
}
```

- [ ] **Step 3: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.controller.TaskallV2ControllerTest"`
Expected: FAIL(`getTwoFactorAuth`/`postTwoFactorAuth`メソッドが存在せずコンパイルエラーになる)

- [ ] **Step 4: TaskallV2Controllerへマッピングとセッション連携を追加する**

`src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java`へ以下の変更を加える。

まず、`getMyPage`メソッドの直後に新規マッピングを追加する:

```java
    @GetMapping("/taskall-v2/service/twoFactorAuth.html")
    public String getTwoFactorAuth(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/twoFactorAuth.html")
    public String postTwoFactorAuth(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }
```

次に`buildContext`メソッドへ、`pendingTwoFactorAccountId`セッション属性を入力コンテキストへ転記する処理を追加する(`accountId`転記の直後に追記する):

```java
        // 二段階認証(一次認証通過・二次認証待ち)中のアカウントIDをセッションから引き継ぐ
        Object pendingTwoFactorAccountId = request.getSession().getAttribute("pendingTwoFactorAccountId");
        if (pendingTwoFactorAccountId != null) {
            context.put("pendingTwoFactorAccountId", (String) pendingTwoFactorAccountId);
        }
```

次に`handleRequest`メソッド内、`storeAccountIdIfExists(request.getSession(), result);`の直後に、二段階認証完了時のセッション属性削除処理の呼び出しを追加する:

```java
        storeAccountIdIfExists(request.getSession(), result);
        clearPendingTwoFactorAccountIdIfCompleted(request.getSession(), result);
        populateModel(result, model);
```

`populateModel`呼び出しの後(`return resolveViewName(result);`の直前)に、Model属性として`pendingTwoFactorAccountId`をセッションから直接設定する処理を追加する(このセッション属性はサービス層の出力JSONを経由せずテンプレートから直接参照するため、`populateModel`とは別に設定する):

```java
        model.addAttribute("pendingTwoFactorAccountId", request.getSession().getAttribute("pendingTwoFactorAccountId"));

        return resolveViewName(result);
```

最後に、`storeAccountIdIfExists`メソッドの直後へ、新規privateメソッドを追加する:

```java
    private void clearPendingTwoFactorAccountIdIfCompleted(HttpSession session, JsonNode result) {
        if (result.path("twoFactorAuthCompleted").asBoolean(false)) {
            session.removeAttribute("pendingTwoFactorAccountId");
        }
    }
```

- [ ] **Step 5: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.controller.TaskallV2ControllerTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 全体テストを実行し既存のコントローラ挙動に影響がないことを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java src/test/java/com/freedom/taskall_v2/web/controller/TaskallV2ControllerTest.java
git commit -m "feat: TaskallV2Controllerに二段階認証画面のマッピングとセッション連携を追加"
```

---

## Task 11: LOGIN_STATUSの期限切れ行を定期削除するスケジューラを実装する

**Files:**
- Create: `src/main/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupScheduler.java`
- Modify: `src/main/java/com/freedom/taskall_v2/TaskallV2Application.java`
- Test: `src/test/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupSchedulerTest.java`

**Interfaces:**
- Consumes: `LoginStatusService.deleteExpired()`(Task 4)。
- Produces: なし(定期実行処理のため、他Taskからは呼び出されない末端の処理)。

**背景:** 設計書「『ログイン試行(LOGIN_STATUS)』の定期クリーンアップ」節の通り、パスワード認証後に
一度もパスコードを入力せず離脱した場合等、`(ACCNT_ID, SESSION_ID)`が再度アクセスされない限り
`LOGIN_STATUS`行が削除されずに残り続けてしまう。SpringBatchほどの仕組みは過剰であるため、
Spring標準の`@Scheduled`による10分間隔の定期実行で対応する。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupSchedulerTest.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginStatusCleanupSchedulerTest {

    @Mock
    private LoginStatusService loginStatusService;

    @InjectMocks
    private LoginStatusCleanupScheduler loginStatusCleanupScheduler;

    @Test
    void 定期実行時にLoginStatusServiceのdeleteExpiredが呼び出されること() {

        when(loginStatusService.deleteExpired()).thenReturn(2);

        loginStatusCleanupScheduler.cleanupExpiredLoginStatus();

        verify(loginStatusService).deleteExpired();
    }
}
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.LoginStatusCleanupSchedulerTest"`
Expected: FAIL(`LoginStatusCleanupScheduler`クラスが存在せずコンパイルエラーになる)

- [ ] **Step 3: LoginStatusCleanupSchedulerを実装する**

`src/main/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupScheduler.java`を新規作成する:

```java
package com.freedom.taskall_v2.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 「ログイン試行(LOGIN_STATUS)」テーブルの期限切れ行を定期的に物理削除するスケジューラです。
 *
 * <p>
 * パスワード認証後にパスコードを一度も入力せず離脱した場合等、そのセッションが再度
 * アクセスしてこない限り行が残り続けてしまうため、10分間隔で有効期限切れの行を一括削除します
 * (設計書「『ログイン試行(LOGIN_STATUS)』の定期クリーンアップ」節)。
 * </p>
 */
@Component
public class LoginStatusCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LoginStatusCleanupScheduler.class);

    private final LoginStatusService loginStatusService;

    public LoginStatusCleanupScheduler(LoginStatusService loginStatusService) {
        this.loginStatusService = loginStatusService;
    }

    /** 10分間隔で、有効期限切れのLOGIN_STATUS行を一括削除する。 */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredLoginStatus() {
        int deletedCount = loginStatusService.deleteExpired();
        logger.info("期限切れのLOGIN_STATUS行を削除しました。deletedCount={}", deletedCount);
    }
}
```

- [ ] **Step 4: テストを実行し成功することを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.web.service.LoginStatusCleanupSchedulerTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: TaskallV2Applicationへ@EnableSchedulingを追加する**

`src/main/java/com/freedom/taskall_v2/TaskallV2Application.java`を以下のように変更する:

```java
package com.freedom.taskall_v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskallV2Application {

	public static void main(String[] args) {
		SpringApplication.run(TaskallV2Application.class, args);
	}

}
```

- [ ] **Step 6: 全体テストを実行しアプリケーションコンテキストの起動に問題がないことを確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミットする**

```bash
git add src/main/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupScheduler.java src/main/java/com/freedom/taskall_v2/TaskallV2Application.java src/test/java/com/freedom/taskall_v2/web/service/LoginStatusCleanupSchedulerTest.java
git commit -m "feat: LOGIN_STATUSの期限切れ行を定期削除するスケジューラを追加"
```

---

## Task 12: 二次認証コード入力画面のマスタデータ(URI_PATTERN〜HTML_PARTS_IN_APROLE)を追加する

**Files:**
- Modify: `src/main/resources/db/data/URI_PATTERN.txt`
- Modify: `src/main/resources/db/data/HTML_PAGE.txt`
- Modify: `src/main/resources/db/data/SCR.txt`
- Modify: `src/main/resources/db/data/SCR_ELM.txt`
- Modify: `src/main/resources/db/data/HTML_PARTS.txt`
- Modify: `src/main/resources/db/data/PARTS_IN_PAGE.txt`
- Modify: `src/main/resources/db/data/PARTS_ITEM.txt`
- Modify: `src/main/resources/db/data/HTML_PARTS_IN_APROLE.txt`
- Test: 既存の`src/test/java/com/freedom/taskall_v2/common/db/DbSchemaSqlGeneratorRealDataTest.java`をそのまま再実行して`src/main/resources/db/sql`配下のSQLを再生成する(新規テストは追加しない)

**Interfaces:**
- Consumes: なし(データ資材のみの追加)。
- Produces: 新画面`/taskall-v2/service/twoFactorAuth.html`のGET(`SCR_ID=1101501`)/POST(`SCR_ID=1101551`)がTask 10の
  コントローラマッピングから呼び出せる状態になる。

**設計判断(ユーザへの確認事項):** 二次認証画面には既存の「リンク一覧領域」(`HTML_PARTS_ID=1000301`)を
**意図的に含めません**。二次認証フローの途中で他画面へ離脱できてしまうと、`LOGIN_STATUS`の状態管理が
複雑化するのを避けるための判断です。マイページ(`1000201`ページ)などと異なり、認証完了までは
システム名・共通ヘッダ・エラーメッセージ表示領域・二次認証コード入力領域の4パーツのみを表示します。

- [ ] **Step 1: URI_PATTERN.txtへ新しいURIパターンを追加する**

`src/main/resources/db/data/URI_PATTERN.txt`の末尾に以下の行を追加する(既存行の末尾に改行を追加してから追記):

```
1002201	二段階認証(二次認証コード入力)	/taskall-v2/service/twoFactorAuth.html	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 2: HTML_PAGE.txtへ新しい画面を追加する**

`src/main/resources/db/data/HTML_PAGE.txt`の末尾に以下の行を追加する。`DESTINATION_POST`は
`VerifyTwoFactorAuthService`(Task 9)が常に自分自身の出力で`respKind`/`destination`を上書きするため
実際には参照されないが、他の行と同様にフォールバック値として`top.html`を設定しておく:

```
1001501	二段階認証(二次認証コード入力)	1002201	1101501	forward	10000_contents.html	1101551	redirect	top.html	0	redirect	top.html	0	redirect	top.html	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 3: SCR.txtへ新しいスクリプトを追加する**

`src/main/resources/db/data/SCR.txt`の末尾に以下の2行を追加する:

```
1101501	二段階認証(二次認証コード入力)画面表示	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1101551	二段階認証(二次認証コード入力)検証	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 4: SCR_ELM.txtへ新しいスクリプト要素を追加する**

`src/main/resources/db/data/SCR_ELM.txt`の末尾に以下の3行を追加する。GET画面表示は既存の
`GetAccountService`→`CreateHtmlService`の並びを踏襲し、POST検証は`VerifyTwoFactorAuthService`
単独(既存の`DeleteRecordService`のような単発PRGパターン)とする:

```
1101501	com.freedom.taskall_v2.web.service.GetAccountService			1101501	1	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1101502	com.freedom.taskall_v2.web.service.CreateHtmlService			1101501	2	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1101551	com.freedom.taskall_v2.web.service.VerifyTwoFactorAuthService			1101551	1	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 5: HTML_PARTS.txtへ新しい画面パーツを追加する**

`src/main/resources/db/data/HTML_PARTS.txt`の末尾に以下の行を追加する:

```
1001201	二次認証コード入力領域	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 6: PARTS_IN_PAGE.txtへ新しい画面のパーツ構成を追加する**

`src/main/resources/db/data/PARTS_IN_PAGE.txt`の末尾に以下の4行を追加する
(`PARTS_IN_PAGE_ID`は`HTML_PAGE_ID + (ORD_IN_GRP - 1)`という既存の採番規則に従う):

```
1001501	1001501	1000001	1	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1001502	1001501	1000002	2	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1001503	1001501	1001101	3	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1001504	1001501	1001201	4	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 7: PARTS_ITEM.txtへsystemName/errMsgListの画面表示項目を追加する**

`src/main/resources/db/data/PARTS_ITEM.txt`の末尾に以下の2行を追加する。共通ヘッダ(`1000002`)と
新設の二次認証コード入力領域(`1001201`)は、既存の`myPage`(`1000202`)/ログイン部品(`1000201`)と
同様に画面表示項目を持たない(静的なフォーム/リンクのみのため)ので行は不要:

```
1001501	systemName	SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'	1001501	1	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1001503	errMsgList	SELECT ERR_MSG FROM ERR_MSG WHERE ERR_MSG_ID = #{errMsgKey}	1001503	3	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 8: HTML_PARTS_IN_APROLE.txtへ新パーツの権限行を追加する**

新設の二次認証コード入力領域(`1001201`)は、パスワード認証は済んだがまだログインが完了していない
(=ゲスト扱いの)ユーザが操作するため、既存のログイン部品(`1000201`)がゲスト/個人/法人/管理者/
特権管理者の全ロールに`edit`権限を持つのと同様に、全5ロールへ`edit`権限を追加する
(`HTML_PARTS_IN_APROLE_ID`はロールごとの既存ブロックの最終行に続く番号を採番する):

```
1000006	1000001	1001201	edit	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1001006	1000101	1001201	edit	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1002005	1000201	1001201	edit	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1003013	1000301	1001201	edit	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
1004013	1000401	1001201	edit	1	0	data_loader	2026-07-31 00:00:00	data_loader	2026-07-31 00:00:00
```

- [ ] **Step 9: REQUIRE_APROLEには行を追加しないことを確認する**

`GetAccountService#checkRequireRole`は、`HTML_PAGE`から`REQUIRE_APROLE`へのLEFT JOINで`APROLE_ID`が
`NULL`の場合(=対象ページに`REQUIRE_APROLE`行が無い場合)は許可判定をスキップする(`myPage.html`が
これに該当し、`REQUIRE_APROLE`に行を持たない)。二次認証画面もログイン前のゲストが必ずアクセスできる
必要があるため、`REQUIRE_APROLE`には**行を追加しない**。

- [ ] **Step 10: DBスキーマSQLを再生成する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test --tests "com.freedom.taskall_v2.common.db.DbSchemaSqlGeneratorRealDataTest"`
Expected: BUILD SUCCESSFUL。実行後、以下のファイルが新規生成されていることを確認する:
`src/main/resources/db/sql/INSERT_URI_PATTERN.sql`, `INSERT_HTML_PAGE.sql`, `INSERT_SCR.sql`,
`INSERT_SCR_ELM.sql`, `INSERT_HTML_PARTS.sql`, `INSERT_PARTS_IN_PAGE.sql`, `INSERT_PARTS_ITEM.sql`,
`INSERT_HTML_PARTS_IN_APROLE.sql`(いずれも既存ファイルの更新、末尾に新規行分のINSERT文が追加されていること)。

- [ ] **Step 11: コミットする**

```bash
git add src/main/resources/db/data/URI_PATTERN.txt src/main/resources/db/data/HTML_PAGE.txt \
  src/main/resources/db/data/SCR.txt src/main/resources/db/data/SCR_ELM.txt \
  src/main/resources/db/data/HTML_PARTS.txt src/main/resources/db/data/PARTS_IN_PAGE.txt \
  src/main/resources/db/data/PARTS_ITEM.txt src/main/resources/db/data/HTML_PARTS_IN_APROLE.txt \
  src/main/resources/db/sql/
git commit -m "feat: 二次認証コード入力画面のマスタデータを追加"
```

---

## Task 13: 二次認証コード入力画面のThymeleafテンプレートを実装する

**Files:**
- Create: `src/main/resources/templates/parts/10130_twoFactorAuth.html`
- Create: `src/main/resources/templates/parts/common/20130_commonTwoFactorAuth.html`
- Modify: `src/main/resources/templates/10000_contents.html`

**Interfaces:**
- Consumes: `part.htmlPartsId`(`CreateHtmlService`が出力する`htmlPage`配列要素、Task 2で追加した
  `HTML_PARTS_ID=1001201`と比較する)、`authList`(既存の権限リスト、`AuthUtil.hasEditAuth`の第2引数)、
  `pendingTwoFactorAccountId`(Task 10で`Model`に設定する、二次認証待ちアカウントIDまたは`null`)。
- Produces: なし(末端のビュー実装)。

- [ ] **Step 1: ラッパーテンプレートを新規作成する**

`src/main/resources/templates/parts/10130_twoFactorAuth.html`を新規作成する。既存の
`10120_errMsgList.html`/`10030_login.html`と同じ「`htmlPartsId`一致 + `hasEditAuth`」パターンとする:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="wrapper(part)"
         th:if="${part.htmlPartsId == '1001201'
                 and T(com.freedom.taskall_v2.web.util.AuthUtil).hasEditAuth('1001201', authList)}">
        <div th:replace="~{parts/common/20130_commonTwoFactorAuth :: body}"></div>
    </div>
</body>
</html>
```

- [ ] **Step 2: 共通部品テンプレートを新規作成する**

`src/main/resources/templates/parts/common/20130_commonTwoFactorAuth.html`を新規作成する。
`pendingTwoFactorAccountId`が`null`の場合(=二次認証待ち状態でないのに直接アクセスされた場合)は
何も表示しない。6桁の数字入力欄と送信ボタンのみのシンプルな構成とし、`20030_commonLogin.html`と
同様に画面中央へカード状に配置する:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <!--/* 一次認証(パスワード)通過後、二次認証(6桁のパスコード)の入力を受け付ける領域。
           パスワード認証済みでもパスコード未入力の間はログインが完了していないため、
           マイページのログインフォーム(20030_commonLogin.html)と同様に画面中央へ配置する */-->
    <div th:fragment="body" th:if="${pendingTwoFactorAccountId != null}" class="1001201 row justify-content-center my-5">
        <div class="col-12 col-md-5 col-lg-4">
            <div class="card shadow-sm">
                <div class="card-body p-4">
                    <h1 class="h5 card-title text-center mb-4">二段階認証</h1>
                    <p class="text-center mb-4">登録済みのメールアドレスに送信した6桁の確認コードを入力してください。</p>
                    <div class="mb-4">
                        <label for="TWO_FACTOR_CODE" class="form-label">確認コード</label>
                        <input id="TWO_FACTOR_CODE" name="TWO_FACTOR_CODE" class="form-control" inputmode="numeric"
                               maxlength="6" autocomplete="one-time-code">
                    </div>
                    <div class="d-grid">
                        <button type="button" class="btn btn-primary" onclick="submitMainForm()">認証する</button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 3: 10000_contents.htmlのループへ新パーツを追加する**

`src/main/resources/templates/10000_contents.html`の`th:each="part : ${htmlPage}"`ループ末尾
(`10120_errMsgList`の直後)に以下の行を追加する:

```diff
                 <div th:replace="~{parts/10120_errMsgList :: wrapper(part=${part})}"></div>
+                <div th:replace="~{parts/10130_twoFactorAuth :: wrapper(part=${part})}"></div>
             </div>
```

- [ ] **Step 4: アプリケーションを起動し画面表示を目視確認する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew bootRun`
としてアプリを起動し、正規のメールアドレス/パスワードで`myPage.html`からログインを試みる。
パスワード認証成功後、`/taskall-v2/service/twoFactorAuth.html`へリダイレクトされ、
「二段階認証」カードが中央に表示されること、開発中に送信されたメール(コンソールログまたは
設定したSMTP経由)に記載の6桁コードを入力すると`myPage.html`へログイン完了した状態で
遷移することを確認する。確認後、`Ctrl+C`でプロセスを終了する。

- [ ] **Step 5: コミットする**

```bash
git add src/main/resources/templates/parts/10130_twoFactorAuth.html \
  src/main/resources/templates/parts/common/20130_commonTwoFactorAuth.html \
  src/main/resources/templates/10000_contents.html
git commit -m "feat: 二次認証コード入力画面のテンプレートを追加"
```

---

## Task 14: 全体テストを実行し、プルリクエストを作成する

**Files:** なし(検証とPR作成のみ)

**Interfaces:** なし(最終確認タスク)

- [ ] **Step 1: 全体テストスイートを実行する**

Run: `JAVA_HOME=/home/develop/jdk21 PATH=/home/develop/jdk21/bin:$PATH ./gradlew test`
Expected: BUILD SUCCESSFUL(Task 1〜13で追加した全テスト、および既存の全テストが成功すること)

- [ ] **Step 2: 未コミットの変更が無いことを確認する**

Run: `git status --short`
Expected: 出力が空であること(Task 1〜13の各コミットで全て取り込み済みであること)

- [ ] **Step 3: リモートへpushする**

```bash
git push -u origin feature/11
```

- [ ] **Step 4: developブランチ宛にプルリクエストを作成する**

`gh pr create --base develop --head feature/11 --title "二段階認証(パスワード+メール確認コード)を実装" --body "$(cat <<'EOF'
## 概要
issue #11の二段階認証(パスワード認証 + メールによる6桁確認コード認証)を実装します。
設計内容は`documents/design/2000006_two_phase_login.md`を参照してください。

## 主な変更点
- `CreateTableSqlBuilder`: TBL_DEF.EXTRAの`UNIQUE_x_y`記法から複合UNIQUE制約を生成できるように拡張
- `LOGIN_STATUS`/`ACCNT_AUTH_LOCK`テーブルを新設し、二次認証の状態・ロックアウト管理を行う
- `LoginStatusService`/`AccntAuthLockService`/`PasscodeGenerator`/`TwoFactorMailService`を新規実装
- `TwoPhaseAuthenticationProvider`でパスワード認証成功後に二次認証要求へ分岐させ、
  `AccountAuthenticationFailureHandler`で二次認証画面/ロック画面への振り分けを行う
- `VerifyTwoFactorAuthService`で二次認証コードのPOST検証(成功/失敗/新規ロック)を実装
- `LoginStatusCleanupScheduler`により`LOGIN_STATUS`の期限切れ行を10分間隔で自動削除
- 二次認証コード入力画面(`/taskall-v2/service/twoFactorAuth.html`)のマスタデータ・Thymeleafテンプレートを追加

## 設計上の補足判断(要レビュー)
- `AccntAuthLockService.isLocked`にて、ロック期限が自然経過した場合は`FAIL_CNT`を0にリセットする
  補完処理を追加しています(設計書に明記はないが、次回ロック判定を正しく再スタートさせるための措置)。
- 二次認証コード入力画面には既存の「リンク一覧領域」を含めていません(認証完了前に他画面へ
  離脱できてしまうことを避けるため)。

Closes #11

🤖 Generated with [Copilot CLI](https://github.com/github/copilot-cli)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
EOF
)"`

Expected: プルリクエストのURLが出力されること。

---
