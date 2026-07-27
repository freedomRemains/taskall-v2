---

# ログについて

[TOP に戻る](../README.md)

- 「@Slf4j」でログ記録している場合、「application.properties」でもログファイル名等のログ設定が可能。
- 特定のログだけを別ファイルに出力するといった細かい要件に対応する場合は、「logback-spring.xml」でログの詳細を指定できる。

ログ設定のサンプルは、次の通り。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- 共通で使うログパターンを変数化 -->
    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n" />

    <!-- ログファイルのディレクトリを変数化 -->
    <property name="LOG_DIR" value="logs" />

    <!-- コンソール出力用のアペンダ -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">

        <!-- エンコーダ設定 -->
        <encoder>

            <!-- ログのフォーマット -->
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- ファイル出力用のアペンダ -->
    <appender name="LOG_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">

        <!-- 出力ファイル設定 -->
        <file>${LOG_DIR}/app.log</file>

        <!-- ログローテーションのポリシー -->
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">

            <!-- 日ごとにログをローテーション -->
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>

            <!-- 保持期間を30日間に設定 -->
            <maxHistory>30</maxHistory>
        </rollingPolicy>

        <!-- エンコーダ設定 -->
        <encoder>

            <!-- ログのフォーマット -->
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- ルートロガーの設定 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="LOG_FILE" />
    </root>

    <!-- MyBatis関連パッケージはDEBUGとする -->
    <logger name="org.apache.ibatis" level="DEBUG"/>
    <logger name="org.mybatis" level="DEBUG"/>

    <!-- 共通ライブラリのうち、MyBatisのパッケージはDEBUGとする(SQLログを出力するため) -->
    <logger name="com.sblib" level="INFO"/>
    <logger name="com.sblib.mapper" level="DEBUG"/>

    <!-- JDBC の PreparedStatement パラメータを出力 -->
    <logger name="jdbc.sqlonly" level="DEBUG"/>
    <logger name="jdbc.sqltiming" level="DEBUG"/>
    <logger name="jdbc.resultset" level="DEBUG"/>
    <logger name="jdbc.audit" level="DEBUG"/>

</configuration>
```

ログを記録する側のサンプルコードは、次の通り。
「@Slf4j」アノテーションにより、ログ記録が可能。

```java
@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

(中略)
        // ログサンプル出力のため、各種ログを記録する
        log.trace("This is a trace log message.");
        log.info("This is an info log message.");
        log.warn("This is a warn log message.");
        log.error("This is an error log message.");
```

所定のログだけを別ファイルに出力する場合は、logback-spring.xml に<loger>タグによるロガー設定を行う。

```xml
    <!-- com.sb.sbwapパッケージのログのみをファイル出力するためのアペンダ -->
    <appender name="SBWAP_LOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <!-- 「LOG_FILE」と同じように設定する -->
    </appender>

    <!-- com.sb.sbwapパッケージのログはSBWAP_LOGアペンダに出力 -->
    <logger name="com.sb.sbwap" level="INFO" additivity="false">
        <appender-ref ref="SBWAP_LOG"/>
    </logger>
```

このように記述すると、logger タグの name で指定したパッケージ配下のログだけ「SBWAP_LOG」への出力に切り替わる。

- addirivity(伝播有無)を false とすることにより、別のログには出力されないようになる。
- 逆に additivity を true にすると、その他のログにも name 属性で指定したパッケージのログが出力されるようになる。

例えば次のようなコードで「SBWAP_LOG」対象外である「com.sb.sblib」パッケージのログを出力した場合、このサービスクラスが出力するログは「SBWAP_LOG」には出力されない。

```Java
package com.sb.sblib.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutputLogService {
    public void outputSampleLogs() {
        log.trace("[sblib(NOT com.sb.sbwap)]This is a strace log message.");
        log.info("[sblib(NOT com.sb.sbwap)]This is an info log message.");
        log.warn("[sblib(NOT com.sb.sbwap)]This is a warn log message.");
        log.error("[sblib(NOT com.sb.sbwap)]This is an error log message.");
    }
}
```

【ログ出力を分ける、呼び出し側のコード】

```Java
        // このログは「SBWAP_LOG」設定により、「sbwap.log」に出力される
        log.trace("This is a trace log message.");
        log.info("This is an info log message.");
        log.warn("This is a warn log message.");
        log.error("This is an error log message.");

        // このログは「com.sb.sblib」パッケージ配下なので、「sbwap.log」に出力されない
        outputLogService.outputSampleLogs();
```

実際にコードを動かしてみると挙動が分かる。(トップ画面アクセスによりログが記録される)  
[http://localhost:8080/top](http://localhost:8080/top)

- 「sbwap.log」には「com.sb.sbwap」パッケージ配下のログのみが出力される。

```log
2025-12-07 11:51:58 [http-nio-8080-exec-1] INFO  c.s.sbwap.controller.LoginController - This is an info log message.
2025-12-07 11:51:58 [http-nio-8080-exec-1] WARN  c.s.sbwap.controller.LoginController - This is a warn log message.
2025-12-07 11:51:58 [http-nio-8080-exec-1] ERROR c.s.sbwap.controller.LoginController - This is an error log message.

```

- 「com.sb.sblib」など「sbwap.log」がカバーしないパッケージのログは「app.log」に出力される。

```log
2025-12-07 11:51:58 [http-nio-8080-exec-1] INFO  c.sb.sblib.service.OutputLogService - [sblib(NOT com.sb.sbwap)]This is an info log message.
2025-12-07 11:51:58 [http-nio-8080-exec-1] WARN  c.sb.sblib.service.OutputLogService - [sblib(NOT com.sb.sbwap)]This is a warn log message.
2025-12-07 11:51:58 [http-nio-8080-exec-1] ERROR c.sb.sblib.service.OutputLogService - [sblib(NOT com.sb.sbwap)]This is an error log message.
```

なおいずれのログも TRACE レベルが出力されていないが、これはルートロガーが「INFO」になっているため。  
※　 INFO > DEBUG > TRACE で TRACE は INFO より優先度が低いので、出力されない。

ロガーの設定方法は様々であり、「@Slf4j」だけで振り分けが可能であればそれに越したことはないが、logger タグのログだけを出力するために、Java のコードでロガーを指定する方法もある。

- 次のコードのように org.slf4j.Logger 及び LoggerFactory により、ロガー名を指定してロガーを取得できる。
- この場合後半の「logger.xxx」呼び出しのログは、「sbwap.log」に出力される。

```Java
package com.sb.sblib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutputLogService {

    private static final Logger logger = LoggerFactory.getLogger("com.sb.sbwap");

    public void outputSampleLogs() {
        log.trace("[sblib(NOT com.sb.sbwap)]This is a strace log message.");
        log.info("[sblib(NOT com.sb.sbwap)]This is an info log message.");
        log.warn("[sblib(NOT com.sb.sbwap)]This is a warn log message.");
        log.error("[sblib(NOT com.sb.sbwap)]This is an error log message.");
        logger.trace("[NOT com.sb.sbwap, BUT logger specified]This is a strace log message.");
        logger.info("[NOT com.sb.sbwap, BUT logger specified]This is an info log message.");
        logger.warn("[NOT com.sb.sbwap, BUT logger specified]This is a warn log message.");
        logger.error("[NOT com.sb.sbwap, BUT logger specified]This is an error log message.");
    }
}
```

logback-spring.xml に任意の名前で logger を定義し、Java 側で org.sfl4j.LoggerFactory によりその logger を取得することで、任意のログを別ファイルに出力することが可能である。  
ログに関する要件は変わることも多いため、単に「@Slf4j」を使うのではなく、それをラッピングしたコンポーネントクラス(例えば「LogComponent」)を定義しておき、ログの振り分けができるようにすることも考慮すべきである。
