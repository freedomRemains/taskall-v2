---
# DBUnit

[READMEに戻る](../../README.md)

DBUnitの利用方法は、次の通り。

- 「build.gradle」に、次の設定を追加する。

```Gradle
	// DBUnitを使用するための設定
	testImplementation 'org.dbunit:dbunit:2.7.3'
	testImplementation 'com.github.springtestdbunit:spring-test-dbunit:1.3.0'
	testImplementation 'org.dbunit:dbunit-xls:1.0.4' // xlsデータセット読み込み用
```

- Javaのテストプログラム内で事前データを投入する場合は、次のように記述する
  - 【重要！】テストクラスに「@Transactional」を付与すると、テスト終了後に自動ロールバックとなる。
  - 「JdbcTemplate」をインジェクションする。
  - 事前データ投入コードを記述する。

```Java
@SpringBootTest
@Transactional
public class UserRepositoryDbUnitTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Test
    void testUserData() throws Exception {
        // 事前データ投入（テスト依存の安定データ）
        jdbcTemplate.update("INSERT INTO users (id, name, age) VALUES (?, ?, ?)",
                999, "TestUser", 30);
```

- もしくは次の通り書いてもよい。
  - 事前データ投入は「@Sql」アノテーションでも実施可能。(これも終了時自動ロールバック対象となる)
  - SQL以外に個別のテストデータを投入したい場合、「JdbcTemplate」を併用できる。

```Java
@SpringBootTest
@Transactional
@Sql({
    "classpath:dbinit/schema.sql",
    "classpath:dbinit/data.sql"
})
public class UserRepositoryDbUnitTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
```

- DBUnitによる照合のコードは、次の通り。

```Java
        // 実際のDBから結果セットを取得
        IDataSet actualDataSet = new DatabaseConnection(
                jdbcTemplate.getDataSource().getConnection()
        ).createDataSet(new String[]{"users"});  // 照合対象テーブルを指定

        // 期待値データセット（Excelから読み込み）
        InputStream in = getClass().getResourceAsStream("/expected_dataset.xls");
        IDataSet expectedDataSet = new XlsDataSet(in);

        // 照合
        ITable expectedTable = expectedDataSet.getTable("users");
        ITable actualTable = actualDataSet.getTable("users");
        Assertion.assertEquals(expectedTable, actualTable);
```

1. EXCELの期待値ファイルのセルが空白ならNULLでassert
1. EXCELの期待値ファイルのセルに [空文字列] と明示的に書いてあったら "" でassert

といったカスタムを行う場合は、次のようなコードを記述して実現する。

- 【推奨】【方法1】期待値ファイルの内容を一部別の扱いにする方法がある。(こちらの方がコードは簡単)

```Java
        // 期待値データセット（Excelから読み込み）
        InputStream in = getClass().getResourceAsStream("/expected_dataset.xls");
        IDataSet expectedDataSet = new XlsDataSet(in);

        // Excelのセルが "[空文字列]" だった場合は、空文字列に変換する
        var replacementDataSet = new ReplacementDataSet(expectedDataSet);
        replacementDataSet.addReplacementObject("[空文字列]", "");
```

- 【方法2】asset失敗時の挙動に割り込んで判定内容を変更する方法がある。

```Java
import org.dbunit.assertion.DefaultFailureHandler;
import org.dbunit.assertion.Difference;
import org.dbunit.assertion.DifferenceListener;
import org.dbunit.assertion.FailureHandler;

public class CustomDifferenceListener implements DifferenceListener {

    private final FailureHandler failureHandler = new DefaultFailureHandler();

    @Override
    public void handle(Difference diff) {

        Object expected = diff.getExpectedValue();
        Object actual = diff.getActualValue();

        // ルール例：
        // セルが空白 (Excel → "") → DBも "" ならOK、nullならNG
        if ("".equals(expected)) {
            if ("".equals(actual)) {
                return; // 差分を無視 → 等価扱い
            }
        }

        // セルが [null] → DBが null ならOK、空文字はNG
        if (expected == null) {
            if (actual == null) {
                return; // 等価扱い
            }
        }

        // 上記に当てはまらなければ差分として扱う
        failureHandler.handle(diff);
    }
}
```

- 更新日時にように、毎回値が変わるので期待値との照合を回避したいカラムがある場合 

次のようなコードで、所定のカラムを期待値照合の対象から除外できる。

```Java
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.Assertion;
...

ITable expectedTable = expectedDataSet.getTable("users");
ITable actualTable = actualDataSet.getTable("users");

// updated_at カラムを除外して比較
ITable filteredExpected = DefaultColumnFilter.excludedColumnsTable(
        expectedTable, new String[]{"updated_at"});
ITable filteredActual = DefaultColumnFilter.excludedColumnsTable(
        actualTable, new String[]{"updated_at"});

// 通常の比較
Assertion.assertEquals(filteredExpected, filteredActual);
```
