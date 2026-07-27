package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 「db/data」配下の資材から「db/sql」配下のSQLファイルを生成する、実運用の生成処理を検証するテストです。
 *
 * <p>
 * 生成されたSQLファイルはリポジトリにコミットする資材であるため、このテストを実行することで
 * 「src/main/resources/db/sql」配下に実際のSQLファイルを生成します。TBL_DEF.txt（データ源）を
 * 変更した場合は、本テストを再実行してSQLファイルを再生成してください。
 * </p>
 */
class DbSchemaSqlGeneratorRealDataTest {

    private final DbSchemaSqlGenerator dbSchemaSqlGenerator = new DbSchemaSqlGenerator();

    @Test
    void 実際のdb_data資材からdb_sql配下にSQLファイルが生成されること() {

        Path dataDir = Path.of("src/main/resources/db/data");
        Path sqlDir = Path.of("src/main/resources/db/sql");

        dbSchemaSqlGenerator.generateAll(dataDir, sqlDir);

        assertThat(Files.exists(sqlDir.resolve("DROP_TBL_DEF.sql"))).isTrue();
        assertThat(Files.exists(sqlDir.resolve("CREATE_TBL_DEF.sql"))).isTrue();
        assertThat(Files.exists(sqlDir.resolve("SELECT_TBL_DEF.sql"))).isTrue();
        assertThat(Files.exists(sqlDir.resolve("INSERT_TBL_DEF.sql"))).isTrue();
    }
}
