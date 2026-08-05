package com.freedom.taskall_v2.common.db;

import java.util.List;
import java.util.Map;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * テーブル定義から、SELECT文を生成するクラスです。
 *
 * <p>
 * 全カラムを定義順に列挙してSELECT対象とし、主キー（サロゲートキー）カラムのみで
 * ORDER BYします（本プロジェクトでは主キーは必ずサロゲートキーであるというルールのため、
 * 主キーのみで一意かつ安定した並び順を実現できます）。
 * </p>
 */
public class SelectSqlBuilder {

    private final MsgUtil msg;

    public SelectSqlBuilder() {
        this(new MsgUtil());
    }

    public SelectSqlBuilder(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * SELECT文を生成します。
     *
     * @param tableName  テーブル物理名
     * @param columnDefs テーブルのカラム定義
     * @return SELECT文
     */
    public String build(String tableName, List<Map<String, String>> columnDefs) {

        // SELECT対象のカラム定義が無い場合はSQLを組み立てられません。TBL_DEF資材自体の不整合であり、
        // システム運用自体が不可能な状態のため、システムエラーとして扱います。
        if (columnDefs == null || columnDefs.isEmpty()) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.columnDefNotFound", tableName));
        }

        // TBL_DEFの定義順を保ったまま、SELECT句で使用するカラム一覧を組み立てます。
        StringBuilder columnPart = new StringBuilder();
        for (Map<String, String> columnDef : columnDefs) {
            if (columnPart.length() > 0) {
                columnPart.append(", ");
            }
            columnPart.append(columnDef.get("FIELD_NAME"));
        }

        // 主キー（サロゲートキー）カラムのみでORDER BY句を組み立てます。
        // 本プロジェクトでは主キーは必ずサロゲートキーであるというルールのため、主キーのみで
        // 一意かつ安定した並び順を実現できます（全カラムでのソートは不要かつ非効率です）。
        StringBuilder orderByPart = new StringBuilder();
        for (Map<String, String> columnDef : columnDefs) {
            if ("PRI".equals(columnDef.get("KEY_DIV"))) {
                if (orderByPart.length() > 0) {
                    orderByPart.append(", ");
                }
                orderByPart.append(columnDef.get("FIELD_NAME"));
            }
        }

        // 主キー定義が見つからない場合はTBL_DEF資材自体の不整合であり、システム運用自体が
        // 不可能な状態のため、システムエラーとして扱います。
        if (orderByPart.length() == 0) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.primaryKeyNotFound", tableName));
        }

        // 取得順序を安定させるため、主キーのみのORDER BY付きのSELECT文を返却します。
        return "SELECT " + columnPart + " FROM " + tableName + " ORDER BY " + orderByPart + ";";
    }
}
