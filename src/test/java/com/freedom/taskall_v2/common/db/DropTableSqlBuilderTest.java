package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DropTableSqlBuilderTest {

    private final DropTableSqlBuilder dropTableSqlBuilder = new DropTableSqlBuilder();

    @Test
    void DROP_TABLE文を生成できること() {

        String sql = dropTableSqlBuilder.build("TBL_DEF");

        assertThat(sql).isEqualTo("DROP TABLE IF EXISTS TBL_DEF;");
    }
}
