package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbSchemaSqlGeneratorTest {

    private final DbSchemaSqlGenerator dbSchemaSqlGenerator = new DbSchemaSqlGenerator();

    @Test
    void TBL_DEFのみのテーブル定義からDROP_CREATE_SELECT_INSERTのSQLファイルが生成されること(@TempDir Path tempDir)
            throws Exception {

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path sqlDir = tempDir.resolve("sql");

        String tblDefContent = "TBL_DEF_ID\tTABLE_NAME\tFIELD_NAME\tTYPE_NAME\tALLOW_NULL\tKEY_DIV\tDEFAULT_VALUE\n"
                + "1\tSAMPLE\tID\tINT\tNO\tPRI\tnull\n"
                + "2\tSAMPLE\tNAME\tTEXT\tNO\t\tnull\n";
        Files.writeString(dataDir.resolve("TBL_DEF.txt"), tblDefContent, StandardCharsets.UTF_8);

        String sampleDataContent = "ID\tNAME\n1\tテスト\n";
        Files.writeString(dataDir.resolve("SAMPLE.txt"), sampleDataContent, StandardCharsets.UTF_8);

        dbSchemaSqlGenerator.generateAll(dataDir, sqlDir);

        assertThat(Files.readString(sqlDir.resolve("DROP_SAMPLE.sql")))
                .contains("DROP TABLE IF EXISTS SAMPLE;");
        assertThat(Files.readString(sqlDir.resolve("CREATE_SAMPLE.sql")))
                .contains("ID INTEGER PRIMARY KEY AUTOINCREMENT");
        assertThat(Files.readString(sqlDir.resolve("SELECT_SAMPLE.sql")))
                .contains("SELECT ID, NAME FROM SAMPLE ORDER BY ID;");
        assertThat(Files.readString(sqlDir.resolve("INSERT_SAMPLE.sql")))
                .contains("INSERT INTO SAMPLE (ID, NAME) VALUES (1, 'テスト');");
    }

    @Test
    void データファイルが存在しないテーブルはINSERT文が生成されないこと(@TempDir Path tempDir) throws Exception {

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path sqlDir = tempDir.resolve("sql");

        String tblDefContent = "TBL_DEF_ID\tTABLE_NAME\tFIELD_NAME\tTYPE_NAME\tALLOW_NULL\tKEY_DIV\tDEFAULT_VALUE\n"
                + "1\tNO_DATA\tID\tINT\tNO\tPRI\tnull\n";
        Files.writeString(dataDir.resolve("TBL_DEF.txt"), tblDefContent, StandardCharsets.UTF_8);

        dbSchemaSqlGenerator.generateAll(dataDir, sqlDir);

        assertThat(Files.exists(sqlDir.resolve("INSERT_NO_DATA.sql"))).isFalse();
        assertThat(Files.exists(sqlDir.resolve("DROP_NO_DATA.sql"))).isTrue();
    }
}
