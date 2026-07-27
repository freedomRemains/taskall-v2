package com.freedom.taskall_v2.common.db.sqlite;

import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.common.db.PrimaryKeyColumnSqlBuilder;

/**
 * SQLite向けの主キーカラム列定義を生成するクラスです。
 *
 * <p>
 * SQLiteのAUTOINCREMENTは「{@code <カラム> INTEGER PRIMARY KEY AUTOINCREMENT}」という
 * 記述が必須のため、型・NOT NULL・DEFAULTの指定を行わず、この固有の書式で出力します
 * （本プロジェクトの主キーは必ず単一カラムの自動採番サロゲートキーであるため、
 * この単純化で問題ありません）。
 * </p>
 */
@Component
public class SqlitePrimaryKeyColumnSqlBuilder implements PrimaryKeyColumnSqlBuilder {

    @Override
    public String build(String fieldName) {
        return fieldName + " INTEGER PRIMARY KEY AUTOINCREMENT";
    }
}
