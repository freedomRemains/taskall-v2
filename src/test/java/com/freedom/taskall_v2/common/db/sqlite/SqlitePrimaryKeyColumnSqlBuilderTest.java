package com.freedom.taskall_v2.common.db.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlitePrimaryKeyColumnSqlBuilderTest {

    private final SqlitePrimaryKeyColumnSqlBuilder sqlitePrimaryKeyColumnSqlBuilder =
            new SqlitePrimaryKeyColumnSqlBuilder();

    @Test
    void SQLite用のAUTOINCREMENT形式で列定義が生成されること() {

        String sql = sqlitePrimaryKeyColumnSqlBuilder.build("ID");

        assertThat(sql).isEqualTo("ID INTEGER PRIMARY KEY AUTOINCREMENT");
    }
}
