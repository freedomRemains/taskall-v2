package com.freedom.taskall_v2.common.db;

import java.util.List;
import java.util.Map;

import com.freedom.taskall_v2.common.db.sqlite.SqlitePrimaryKeyColumnSqlBuilder;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * TBL_DEFのカラム定義から、CREATE TABLE文を生成するクラスです。
 *
 * <p>
 * 主キーカラム（{@code KEY_DIV=PRI}）の自動採番記法はDB製品ごとに異なる
 * （例: SQLiteは{@code AUTOINCREMENT}、MySQLは{@code AUTO_INCREMENT}）ため、
 * {@link PrimaryKeyColumnSqlBuilder}として切り出し、DB製品ごとの実装
 * （{@code common/db/[個別データベース名]}パッケージ）を注入して使用します。
 * 本プロジェクトの主キーは必ず単一カラムの自動採番サロゲートキーであるため、
 * それ以外のカラムのみ型・NOT NULL・DEFAULTの指定を行います。
 * </p>
 */
public class CreateTableSqlBuilder {

    private final MsgUtil msg;
    private final PrimaryKeyColumnSqlBuilder primaryKeyColumnSqlBuilder;

    public CreateTableSqlBuilder() {
        this(new MsgUtil(), new SqlitePrimaryKeyColumnSqlBuilder());
    }

    public CreateTableSqlBuilder(MsgUtil msg, PrimaryKeyColumnSqlBuilder primaryKeyColumnSqlBuilder) {
        this.msg = msg;
        this.primaryKeyColumnSqlBuilder = primaryKeyColumnSqlBuilder;
    }

    /**
     * CREATE TABLE文を生成します。
     *
     * @param tableName  テーブル物理名
     * @param columnDefs TBL_DEF由来のカラム定義行のリスト（1行=1カラム、定義順）
     * @return CREATE TABLE文
     */
    public String build(String tableName, List<Map<String, String>> columnDefs) {

        // 対象テーブルに対するカラム定義が取得できていることを確認する
        if (columnDefs == null || columnDefs.isEmpty()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.common.db.columnDefNotFound", tableName));
        }

        // CREATE TABLE句を開始し、定義順のまま各カラム定義を連結する
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        StringBuilder columnPart = new StringBuilder();
        for (Map<String, String> columnDef : columnDefs) {
            if (columnPart.length() > 0) {
                columnPart.append(",\n");
            }
            columnPart.append("    ").append(buildColumnDefinition(columnDef));
        }

        sql.append(columnPart).append("\n);");
        return sql.toString();
    }

    private String buildColumnDefinition(Map<String, String> columnDef) {

        String fieldName = columnDef.get("FIELD_NAME");
        String typeName = columnDef.get("TYPE_NAME");
        String allowNull = columnDef.get("ALLOW_NULL");
        String keyDiv = columnDef.get("KEY_DIV");
        String defaultValue = columnDef.get("DEFAULT_VALUE");

        // 主キー(サロゲートキー)は、DB製品ごとの自動採番記法に従う
        if ("PRI".equals(keyDiv)) {
            return primaryKeyColumnSqlBuilder.build(fieldName);
        }

        // 通常カラムは型・NULL制約・DEFAULT値を順に付与して定義文字列を組み立てる
        StringBuilder column = new StringBuilder();
        column.append(fieldName).append(" ").append(typeName);
        if ("NO".equals(allowNull)) {
            column.append(" NOT NULL");
        }
        if (isSpecified(defaultValue)) {
            if (isCurrentKeyword(defaultValue)) {
                column.append(" DEFAULT ").append(defaultValue);
            } else {
                column.append(" DEFAULT '").append(defaultValue).append("'");
            }
        }
        return column.toString();
    }

    private boolean isSpecified(String value) {
        return value != null && !value.isEmpty() && !"null".equals(value);
    }

    private boolean isCurrentKeyword(String value) {
        String upper = value.toUpperCase();
        return "CURRENT_DATE".equals(upper) || "CURRENT_TIME".equals(upper) || "CURRENT_TIMESTAMP".equals(upper);
    }
}
