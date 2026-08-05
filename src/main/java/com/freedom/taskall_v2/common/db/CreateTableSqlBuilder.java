package com.freedom.taskall_v2.common.db;

import java.util.List;
import java.util.Map;

import com.freedom.taskall_v2.common.db.sqlite.SqlitePrimaryKeyColumnSqlBuilder;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
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
        // (TBL_DEF資材自体の不整合であり、システム運用自体が不可能な状態のため、システムエラーとする)
        if (columnDefs == null || columnDefs.isEmpty()) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.columnDefNotFound", tableName));
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

        sql.append(columnPart);
        String uniqueConstraintPart = buildUniqueConstraintPart(columnDefs);
        if (!uniqueConstraintPart.isEmpty()) {
            sql.append(",\n").append(uniqueConstraintPart);
        }
        sql.append("\n);");
        return sql.toString();
    }

    /**
     * EXTRA列の"UNIQUE_<グループ番号>_<グループ内順序>"記法を解釈し、
     * グループごとの複合UNIQUE制約句を生成する。
     */
    private String buildUniqueConstraintPart(List<Map<String, String>> columnDefs) {

        // グループ番号ごとに、グループ内順序をキーとしてカラム名を集める
        Map<Integer, java.util.TreeMap<Integer, String>> groupedColumns = new java.util.TreeMap<>();
        java.util.regex.Pattern uniquePattern = java.util.regex.Pattern.compile("^UNIQUE_(\\d+)_(\\d+)$");

        for (Map<String, String> columnDef : columnDefs) {
            String extra = columnDef.get("EXTRA");
            if (extra == null || extra.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher matcher = uniquePattern.matcher(extra);
            if (!matcher.matches()) {
                continue;
            }
            int groupNo = Integer.parseInt(matcher.group(1));
            int ordInGroup = Integer.parseInt(matcher.group(2));
            groupedColumns.computeIfAbsent(groupNo, key -> new java.util.TreeMap<>())
                    .put(ordInGroup, columnDef.get("FIELD_NAME"));
        }

        // グループ番号の昇順に、"UNIQUE (col1, col2)"句を連結する
        StringBuilder uniquePart = new StringBuilder();
        for (java.util.TreeMap<Integer, String> columns : groupedColumns.values()) {
            if (uniquePart.length() > 0) {
                uniquePart.append(",\n");
            }
            uniquePart.append("    UNIQUE (").append(String.join(", ", columns.values())).append(")");
        }
        return uniquePart.toString();
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
