package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InsertSqlBuilderTest {

    private final InsertSqlBuilder insertSqlBuilder = new InsertSqlBuilder();

    @Test
    void データ行ごとにINSERT文を生成できること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT"), buildColumnDef("NAME", "TEXT"));
        List<Map<String, String>> dataRows = List.of(buildDataRow("ID", "1", "NAME", "テスト"),
                buildDataRow("ID", "2", "NAME", "テスト2"));

        List<String> insertSqlList = insertSqlBuilder.build("SAMPLE", columnDefs, dataRows);

        assertThat(insertSqlList).containsExactly(
                "INSERT INTO SAMPLE (ID, NAME) VALUES (1, 'テスト');",
                "INSERT INTO SAMPLE (ID, NAME) VALUES (2, 'テスト2');");
    }

    @Test
    void 数値型でnull文字列や空文字列は0として出力されること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT"), buildColumnDef("COUNT", "INT"));
        List<Map<String, String>> dataRows = List.of(buildDataRow("ID", "1", "COUNT", "null"));

        List<String> insertSqlList = insertSqlBuilder.build("SAMPLE", columnDefs, dataRows);

        assertThat(insertSqlList).containsExactly("INSERT INTO SAMPLE (ID, COUNT) VALUES (1, 0);");
    }

    @Test
    void 文字列型でnull文字列はNULLとして出力されること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT"), buildColumnDef("NAME", "TEXT"));
        List<Map<String, String>> dataRows = List.of(buildDataRow("ID", "1", "NAME", "null"));

        List<String> insertSqlList = insertSqlBuilder.build("SAMPLE", columnDefs, dataRows);

        assertThat(insertSqlList).containsExactly("INSERT INTO SAMPLE (ID, NAME) VALUES (1, NULL);");
    }

    @Test
    void データ行が空の場合は空リストが返却されること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT"));

        List<String> insertSqlList = insertSqlBuilder.build("SAMPLE", columnDefs, List.of());

        assertThat(insertSqlList).isEmpty();
    }

    @Test
    void 値中のシングルクオートは自動的にエスケープされること() {

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ID", "INT"), buildColumnDef("NAME", "TEXT"));
        List<Map<String, String>> dataRows = List.of(buildDataRow("ID", "1", "NAME", "it's not escaped"));

        List<String> insertSqlList = insertSqlBuilder.build("SAMPLE", columnDefs, dataRows);

        assertThat(insertSqlList).containsExactly("INSERT INTO SAMPLE (ID, NAME) VALUES (1, 'it''s not escaped');");
    }

    private Map<String, String> buildColumnDef(String fieldName, String typeName) {
        Map<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        columnDef.put("TYPE_NAME", typeName);
        return columnDef;
    }

    private Map<String, String> buildDataRow(String key1, String value1, String key2, String value2) {
        Map<String, String> dataRow = new LinkedHashMap<>();
        dataRow.put(key1, value1);
        dataRow.put(key2, value2);
        return dataRow;
    }
}
