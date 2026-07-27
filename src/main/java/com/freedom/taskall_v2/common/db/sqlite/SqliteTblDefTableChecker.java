package com.freedom.taskall_v2.common.db.sqlite;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.common.db.TblDefTableChecker;

/**
 * SQLite向けに、「TBL_DEF」テーブルの存在確認を行うクラスです。
 *
 * <p>
 * SQLiteのシステムカタログである{@code sqlite_master}を使用します。
 * </p>
 */
@Component
public class SqliteTblDefTableChecker implements TblDefTableChecker {

    private static final String TBL_DEF_TABLE_NAME = "TBL_DEF";

    private final JdbcTemplate jdbcTemplate;

    public SqliteTblDefTableChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsTblDefTable() {
        // SQLiteのシステムテーブルを参照し、TBL_DEFという名前のテーブルが存在するかを確認します。
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", String.class,
                TBL_DEF_TABLE_NAME);
        return !tableNames.isEmpty();
    }
}
