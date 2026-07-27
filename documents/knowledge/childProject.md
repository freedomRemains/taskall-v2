---
# 子プロジェクトの配置方法

[READMEに戻る](../../README.md)

- 親プロジェクト側で「settings.gradle」に、記述を追加する。
  - 「include('[取り込む子プロジェクトのフォルダ]')」を記述する。
  - 「rootProject～」により、取り込む子プロジェクトに親プロジェクト名を付与。  
    これにより、複数の親プロジェクトで同じ子プロジェクトを取り込んでも、  
    バッティングしないプロジェクト名とできる。(eclipseなどでも取り込める  
    ようにするために必要な設定)

```
[settings.gradle]

include('sblib')

rootProject.name = 'sbwap'
rootProject.children.each { it.name = rootProject.name + '_' + it.name }
```

- 親プロジェクト側で「build.gradle」に、記述を追加する。
  - このとき記述するのは「settings.gradle」で設定した親プロジェクト名を  
    プレフィックスとする、バッティングしないプロジェクト名とすること。

```
[build.gradle]

// ライブラリプロジェクトを使用するための設定
implementation project(':sbwap_sblib')
```

子プロジェクト側は単独でアプリとして起動する必要がなくなる。  
そのため、次のような設定によりアプリケーションクラスを除外する必要がある。

- 子プロジェクトのアプリケーションクラスを削除する。

```Java
【次の子プロジェクトのアプリケーションクラスは、ファイルごと削除する】
package com.sb.sblib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SblibApplication {

    public static void main(String[] args) {
        SpringApplication.run(SblibApplication.class, args);
    }
}
```

- 子プロジェクトは起動可能jar(bootJar)とする必要がないのでjar生成の設定に変更する。(Gradle記述追加)

```Gradle
// 共通ライブラリは起動可能jar(bootJar)とする必要がないのでjar生成の設定に変更する
[build.gradle]
bootJar {
	enabled = false
}
jar {
	enabled = true
}
```

- 子プロジェクトではテスト用に、次のようなアプリケーションクラスの代わりとなるクラスを配置する。

```Java
package com.sb.sblib.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.sb.sblib"
})
@MapperScan({
    "com.sb.sblib.mapper",
})
public class TestSpringBootApplication {
}
```

- 子プロジェクト側の各テストクラスでは、明示的にテスト用アプリケーションクラスを指定する。
  - これまで単に「@SpringBootTest」と記述していた箇所を変更する必要がある。
  - 次のように「@SpringBootTest」で、「classes」を明示的に指定するコードを記述する。

```Java
package com.sb.sblib;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {
    com.sb.sblib.config.TestSpringBootApplication.class
})
class SblibApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- 次のマッパーテストクラスのように、インジェクションが必要となるクラスは同様の修正が必要。
  - 「@SpringBootTest」で、「classes」を明示的に指定する。
  - なおMockitoを使うテストは「@SpringBootTest」の指定が不要のため影響を受けない。

```Java
/**
 * Mapperのテストは所定のDB初期化SQLを実行し、実物のクエリが意図した通りに動くことを確認する。
 */
@SpringBootTest(classes = {
    com.sb.sblib.config.TestSpringBootApplication.class
})
@Sql({
    "classpath:dbinit/schema.sql",
    "classpath:dbinit/data.sql"
})
public class AccountMapperTest {

    @Autowired
    private AccountMapper accountMapper;
(以下、略)
```

- 親プロジェクト側のアプリケーションクラスでは、「@MapperScan」の記述を追加する必要あり。
  - 子プロジェクトにアプリケーションクラスがある場合は、そちらが処理するので記述しなくても動く。
  - 上記のように子プロジェクトからアプリケーションクラスを除外した場合は、明示的な記述が必要。

```Java
package com.sb.sbwap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 取り込む対象のsblibのパッケージ配下のパッケージを認識できるよう、SpringBootの設定を変更する
@SpringBootApplication(scanBasePackages = {
    "com.sb.sbwap",
    "com.sb.sblib",
})
@MapperScan({
    "com.sb.sblib.mapper",
})
public class SbwapApplication {

    public static void main(String[] args) {
        SpringApplication.run(SbwapApplication.class, args);
    }
}
```
