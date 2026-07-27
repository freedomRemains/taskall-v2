---
# JUnit

[READMEに戻る](../../README.md)

JUnitでの単体テストはMockitoを使用する。理由は、次の通り。

- 親と子の挙動だけ規定すればよく、枝葉まで見ると4～5階層になるような複雑なロジックもテストしやすい。

  - A -> B -> C -> D -> Eのような階層のテストであっても、A -> B、B -> C...というように分割できる。
  - たとえばA -> Bのテストの場合、子であるBをモックにする。

- 条件分岐1つに対して1つのモックを使ったテストを作るため、移植性が高い。

  - DB含めて全て実物を使い、サービスの入口と出口とDBを検証するやり方もあるが、複雑になりやすい。

    - 条件分岐を考慮したDB事前データ、入力データが必要で、出口の出力データ検証、DB事後検証も複雑になりやすい。
    - 大規模のロジック変更があるとテストそのものを作り直す必要がでてきてしまう。

  - その点、モックを使ったものは条件分岐ごとに項目が分かれている点で柔軟性に優れている。

    - 大規模なロジック変更があった場合でも生きているルートはそのまま移植できる。
    - 変わった箇所も前述のように親と子の関係だけでテストを閉じられるため、再構築しやすい。

- DBの実物を必要としないため、CI/CDなどでもDBの心配をする必要がない。

Mokitoの使用方法は、次の通り。

- 「build.gradle」に次の設定を追加する。

```Gradle
	// Mockitoを使用するための設定
	testImplementation 'org.mockito:mockito-core:5.14.2'
```

- JUnitに「[テスト対象クラス]Test」というテストクラスを追加する。  
- テスト対象のクラスに「@ExtendWith(MockitoExtension.class)」アノテーションを付与する。

```Java
@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
```

- テストのためモックとしたいクラスに「@Mock」アノテーションを付与する。

```Java
    @Mock
    private AccountMapper accountMapper;
```

- テスト対象のクラスに「@InjectMocks」アノテーションを付与する。

```Java
    @InjectMocks
    private AccountService accountService;
```

- 次のようなコードでモックの挙動を事前設定する。

```Java
        // accountMapperのfindAllメソッドの返却値を設定する
        List<Account> accountList = new ArrayList<Account>();
        Account account = new Account();
        account.setMailAddress("guest@sblib.com");
        account.setPassword("pass");
        accountList.add(account);
        when(accountMapper.findAll()).thenReturn(accountList);
```

- テスト対象のメソッドを呼び出し、想定通りの挙動となるか検証する。

```Java
        // テスト対象のメソッドを実行する
        assertTrue(accountService.auth("guest@sblib.com", "pass"));
```

- 上記テストでは、「accountService.auth」にユーザ名、パスワードを渡したときの挙動を確認している。  
- 本来はDBにデータを取りに行ってユーザ名・パスワードを照合するが、本ケースではDBアクセスのマッパーはモックである。  
- 「when～then」で事前設定したモックの挙動に従い、サービス内では「guest@sblib.com」を含むリストが取得される。  
結果として認証は成功し、「accountService.auth」がtrueを返却するのでそれをassertで検証している。
- 認証失敗のパターンを試したい場合は、検証コードを次のように変更すればよい。

```Java
        // テスト対象のメソッドを実行する
        assertFalse(accountService.auth("master@sblib.com", "pass"));
```

- モックが返却するのは「guest@sblib.com」なので、「master@sblib.com」は認証エラーとなる。
- このように、テスト対象クラス(この場合はサービスクラス)内部の挙動を予めモックで設定してテストを行う。
