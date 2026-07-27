package com.freedom.taskall_v2.common.db;

/**
 * テーブル物理名から、DROP TABLE文を生成するクラスです。
 */
public class DropTableSqlBuilder {

    /**
     * DROP TABLE文を生成します。
     *
     * @param tableName テーブル物理名
     * @return DROP TABLE文
     */
    public String build(String tableName) {
        // 指定されたテーブルを安全に削除できるよう、IF EXISTS付きのDROP文を組み立てます。
        return "DROP TABLE IF EXISTS " + tableName + ";";
    }
}
