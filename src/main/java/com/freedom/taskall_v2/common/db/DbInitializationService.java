package com.freedom.taskall_v2.common.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 事前生成済みのSQL（{@code classpath:db/sql/*.sql}）を使って、
 * DROP／CREATE／INSERTを実行するクラスです。
 *
 * <p>
 * 呼び出し元（{@link DbInitializer}）から独立したSpring Beanとすることで、
 * {@code @Transactional}によるコミット箇所の一元化（自己呼び出しではプロキシが
 * 効かずトランザクションが機能しない問題を避けるため）を実現しています。
 * </p>
 */
@Service
public class DbInitializationService {

    private final JdbcTemplate jdbcTemplate;
    private final TableDefLoader tableDefLoader;
    private final MsgUtil msg;

    /**
     * {@link TableDefLoader}は依存を持たない補助クラスのため、Springのコンストラクタ
     * インジェクション対象を1つに保つ目的で、内部で直接インスタンス化します。
     */
    public DbInitializationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableDefLoader = new TableDefLoader();
        this.msg = new MsgUtil();
    }

    /**
     * 「db/data/TBL_DEF.txt」に定義された全テーブルについて、DROP -> CREATE -> INSERTの順に、
     * 事前生成済みのSQLファイルを実行します。
     */
    @Transactional
    public void initializeDatabase() {

        // TBL_DEFの定義順を維持したまま、初期化対象テーブルの一覧を取得する
        List<String> tableNames = loadTableNamesInOrder();

        // 依存関係を崩さないよう、DROP→CREATE→INSERTをそれぞれ定義順で順次実行する
        for (String tableName : tableNames) {
            executeSqlResourceIfPresent("db/sql/DROP_" + tableName + ".sql");
        }
        for (String tableName : tableNames) {
            executeSqlResourceIfPresent("db/sql/CREATE_" + tableName + ".sql");
        }
        for (String tableName : tableNames) {
            executeSqlResourceIfPresent("db/sql/INSERT_" + tableName + ".sql");
        }
    }

    private List<String> loadTableNamesInOrder() {

        // TBL_DEFを読み込み、定義ファイルに記載された順序のままテーブル名を取り出す
        ClassPathResource tblDefResource = new ClassPathResource("db/data/TBL_DEF.txt");
        try (InputStream inputStream = tblDefResource.getInputStream()) {
            LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap =
                    tableDefLoader.load(inputStream);
            return new ArrayList<>(tableDefMap.keySet());
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.tblDefReadFailed"), e);
        }
    }

    private void executeSqlResourceIfPresent(String resourcePath) {

        // 対応するSQLリソースが存在しない場合は、その処理を何もせずスキップする
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return;
        }

        // 1ファイル内の複数SQLをセミコロンで分割し、空文を除いて順に実行する
        String sqlFileContent = readResourceAsString(resource);
        for (String sql : sqlFileContent.split(";")) {
            String trimmedSql = sql.strip();
            if (!trimmedSql.isEmpty()) {
                jdbcTemplate.execute(trimmedSql + ";");
            }
        }
    }

    private String readResourceAsString(ClassPathResource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.sqlResourceReadFailed", resource), e);
        }
    }
}
