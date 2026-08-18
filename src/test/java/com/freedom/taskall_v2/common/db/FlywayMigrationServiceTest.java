package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.core.io.ClassPathResource;

/**
 * {@link FlywayMigrationService}の実DB(SQLite)を使った検証テストです。
 *
 * <p>
 * Flyway自体はインタフェースを持たないためMockitoでのモック化に向かず、実際のSQLite(一時ファイル)を
 * 使って、issue #72で導入したベースライン戦略(新規ブートストラップ時/既存DB時)が意図通りに
 * 分岐することを検証します。
 * </p>
 */
class FlywayMigrationServiceTest {

    private static final String[] BASE_TABLES = { "TBL_DEF", "ACCNT", "GNR_KEY_VAL", "URI_PATTERN", "HTML_PARTS",
            "SCR", "SCR_ELM", "HTML_PAGE", "PARTS_IN_PAGE", "PARTS_ITEM", "HTML_PARTS_IN_APROLE" };

    @Test
    void 既存DBの場合はV1にベースライン化したうえで未適用のマイグレーションを適用すること(@TempDir Path tempDir) throws Exception {

        DataSource dataSource = createDataSource(tempDir.resolve("legacy.db"));
        // PASSWORD_RESET以外の既存テーブルのみを作成し、Flyway導入前の本番DBを模する。
        executeCreateSqlResources(dataSource, BASE_TABLES);

        DbBootstrapState dbBootstrapState = new DbBootstrapState();
        dbBootstrapState.setFreshlyBootstrapped(false);
        new FlywayMigrationService(dataSource, dbBootstrapState).migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "PASSWORD_RESET")).isTrue();
            assertThat(countRows(connection, "GNR_KEY_VAL", "GNR_KEY_VAL_ID = 1000405")).isEqualTo(1);
            assertThat(countRows(connection, "flyway_schema_history", "version = '1' AND type = 'BASELINE'"))
                    .isEqualTo(1);
            assertThat(countRows(connection, "flyway_schema_history", "version = '2' AND success = 1")).isEqualTo(1);
        }
    }

    @Test
    void 新規ブートストラップ済みDBの場合は最新バージョンとしてベースライン化しマイグレーションを再実行しないこと(@TempDir Path tempDir)
            throws Exception {

        DataSource dataSource = createDataSource(tempDir.resolve("fresh.db"));
        // DbInitializerが最新スキーマ(PASSWORD_RESET・SIGN_UP含む)を新規作成済みの状態を模する。
        executeCreateSqlResources(dataSource, BASE_TABLES);
        executeCreateSqlResources(dataSource, new String[] { "PASSWORD_RESET", "SIGN_UP" });

        DbBootstrapState dbBootstrapState = new DbBootstrapState();
        dbBootstrapState.setFreshlyBootstrapped(true);
        new FlywayMigrationService(dataSource, dbBootstrapState).migrate();

        try (Connection connection = dataSource.getConnection()) {
            // 既にPASSWORD_RESET・SIGN_UPが存在する状態でマイグレーションが実行されるとテーブル重複
            // エラーになるはずだが、ベースライン化により実行自体がスキップされるため、
            // GNR_KEY_VALへの新規行INSERTも行われていないことを確認する。
            assertThat(countRows(connection, "GNR_KEY_VAL", "GNR_KEY_VAL_ID = 1000405")).isEqualTo(0);
            assertThat(countRows(connection, "flyway_schema_history", "version = '5' AND type = 'BASELINE'"))
                    .isEqualTo(1);
        }
    }

    private DataSource createDataSource(Path dbFilePath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFilePath);
        return dataSource;
    }

    private void executeCreateSqlResources(DataSource dataSource, String[] tableNames) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String tableName : tableNames) {
                statement.execute(readClassPathResource("db/sql/CREATE_" + tableName + ".sql"));
            }
        }
    }

    private String readClassPathResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private int countRows(Connection connection, String tableName, String whereClause) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement
                        .executeQuery("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
