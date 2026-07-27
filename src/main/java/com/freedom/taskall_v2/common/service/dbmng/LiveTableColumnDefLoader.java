package com.freedom.taskall_v2.common.service.dbmng;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * ライブDBの「TBL_DEF」テーブルから、指定テーブルのカラム定義を取得するクラスです。
 *
 * <p>
 * {@code GetAllTableCreateSqlService}/{@code GetAllTableSelectSqlService}/
 * {@code GetAllTableInsertSqlService}など、SQL生成にカラム定義(型・NULL可否等)を必要とする
 * 複数のサービスから共通利用します。
 * </p>
 */
@Component
class LiveTableColumnDefLoader {

    private static final String COLUMN_DEF_SQL = """
            SELECT *
            FROM TBL_DEF
            WHERE TABLE_NAME = ?
            ORDER BY TBL_DEF_ID
            """;

    private final RecordQueryService recordQueryService;
    private final MsgUtil msg;

    LiveTableColumnDefLoader(RecordQueryService recordQueryService, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.msg = msg;
    }

    /**
     * 指定テーブルのカラム定義を、TBL_DEF_IDの並び順(定義順)で取得します。
     *
     * @param tableName テーブル物理名
     * @return カラム定義行のリスト(定義順)
     */
    List<Map<String, String>> load(String tableName) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(COLUMN_DEF_SQL, List.of(tableName));

        // GetTableNameListServiceが返すtableNameは同じくTBL_DEFから取得したものだが、
        // 実行タイミングのズレ等で対象テーブルの定義が消えている場合に備え、念のため検証する
        if (rows.isEmpty()) {
            throw new ApplicationInternalException(
                    msg.get("msg.err.common.service.dbmng.tableDefNotFoundOnLiveDb", tableName));
        }

        return new ArrayList<>(rows);
    }
}

