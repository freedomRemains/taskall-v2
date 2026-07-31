package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CreateTableSqlBuilderTest {

    private final CreateTableSqlBuilder createTableSqlBuilder = new CreateTableSqlBuilder();

    @Test
    void 主キーカラムはSQLite用のAUTOINCREMENT形式で出力されること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT", "NO", "PRI", "null"));

        String sql = createTableSqlBuilder.build("SAMPLE", columnDefs);

        assertThat(sql).isEqualTo("CREATE TABLE IF NOT EXISTS SAMPLE (\n"
                + "    ID INTEGER PRIMARY KEY AUTOINCREMENT\n"
                + ");");
    }

    @Test
    void NOT_NULL制約とDEFAULT値が出力されること() {

        List<Map<String, String>> columnDefs = List.of(
                buildColumnDef("ID", "INT", "NO", "PRI", "null"),
                buildColumnDef("NAME", "TEXT", "NO", "", "null"),
                buildColumnDef("VERSION", "INT", "NO", "", "1"),
                buildColumnDef("CRT_DATE", "TEXT", "YES", "", "CURRENT_TIMESTAMP"));

        String sql = createTableSqlBuilder.build("SAMPLE", columnDefs);

        assertThat(sql).isEqualTo("CREATE TABLE IF NOT EXISTS SAMPLE (\n"
                + "    ID INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    NAME TEXT NOT NULL,\n"
                + "    VERSION INT NOT NULL DEFAULT '1',\n"
                + "    CRT_DATE TEXT DEFAULT CURRENT_TIMESTAMP\n"
                + ");");
    }

    @Test
    void カラム定義が空の場合は例外がスローされること() {

        org.junit.jupiter.api.function.Executable executable = () -> createTableSqlBuilder.build("SAMPLE",
                List.of());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.freedom.taskall_v2.common.exception.BusinessRuleViolationException.class, executable);
    }

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

    private Map<String, String> buildColumnDef(String fieldName, String typeName, String allowNull, String keyDiv,
            String defaultValue) {
        Map<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        columnDef.put("TYPE_NAME", typeName);
        columnDef.put("ALLOW_NULL", allowNull);
        columnDef.put("KEY_DIV", keyDiv);
        columnDef.put("DEFAULT_VALUE", defaultValue);
        return columnDef;
    }

    private Map<String, String> buildColumnDefWithExtra(String fieldName, String typeName, String allowNull,
            String keyDiv, String defaultValue, String extra) {
        Map<String, String> columnDef = buildColumnDef(fieldName, typeName, allowNull, keyDiv, defaultValue);
        columnDef.put("EXTRA", extra);
        return columnDef;
    }
}
