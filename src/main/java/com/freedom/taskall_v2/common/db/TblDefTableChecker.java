package com.freedom.taskall_v2.common.db;

/**
 * 「TBL_DEF」テーブルが既に存在するかどうかを判定するインタフェースです。
 *
 * <p>
 * テーブル存在確認の方法はDB製品ごとに異なる(例: SQLiteは{@code sqlite_master}、MySQLは
 * {@code information_schema.tables})ため、DB製品ごとの実装を
 * {@code common/db/[個別データベース名]}パッケージに配置し、{@link DbInitializer}へ
 * 差し替え可能な形で注入します。
 * </p>
 */
public interface TblDefTableChecker {

    /**
     * 「TBL_DEF」テーブルが既に存在するかどうかを判定します。
     *
     * @return 既に存在する場合はtrue
     */
    boolean existsTblDefTable();
}
