package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SelectSqlBuilderTest {

    private final SelectSqlBuilder selectSqlBuilder = new SelectSqlBuilder();

    @Test
    void 主キーカラムのみでORDERBYしたSELECT文を生成できること() {

        Map<String, String> idColumn = new LinkedHashMap<>();
        idColumn.put("FIELD_NAME", "ID");
        idColumn.put("KEY_DIV", "PRI");
        Map<String, String> nameColumn = new LinkedHashMap<>();
        nameColumn.put("FIELD_NAME", "NAME");

        String sql = selectSqlBuilder.build("SAMPLE", List.of(idColumn, nameColumn));

        assertThat(sql).isEqualTo("SELECT ID, NAME FROM SAMPLE ORDER BY ID;");
    }

    @Test
    void カラム定義が空の場合は例外がスローされること() {

        org.junit.jupiter.api.function.Executable executable = () -> selectSqlBuilder.build("SAMPLE", List.of());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.freedom.taskall_v2.common.exception.ApplicationInternalException.class, executable);
    }

    @Test
    void 主キー定義が無い場合は例外がスローされること() {

        Map<String, String> nameColumn = new LinkedHashMap<>();
        nameColumn.put("FIELD_NAME", "NAME");

        org.junit.jupiter.api.function.Executable executable =
                () -> selectSqlBuilder.build("SAMPLE", List.of(nameColumn));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.freedom.taskall_v2.common.exception.ApplicationInternalException.class, executable);
    }
}
