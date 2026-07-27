package com.freedom.taskall_v2.common.db;

/**
 * 主キー(サロゲートキー)カラムのCREATE TABLE用列定義を生成するインタフェースです。
 *
 * <p>
 * 自動採番の記法はDB製品ごとに異なる(例: SQLiteは{@code AUTOINCREMENT}、MySQLは
 * {@code AUTO_INCREMENT})ため、DB製品ごとの実装を{@code common/db/[個別データベース名]}
 * パッケージに配置し、{@link CreateTableSqlBuilder}へ差し替え可能な形で注入します。
 * </p>
 */
public interface PrimaryKeyColumnSqlBuilder {

    /**
     * 主キーカラムの列定義を生成します。
     *
     * @param fieldName 主キーのカラム物理名
     * @return 主キーカラムの列定義
     */
    String build(String fieldName);
}
