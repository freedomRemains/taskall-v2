package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * テーブルデータメンテナンス画面から渡される{@code tableName}パラメータの妥当性を
 * 検証するサービスです。
 *
 * <p>
 * テーブル名/カラム名はJDBCの{@code ?}バインドパラメータで指定できないため、動的SQLを
 * 組み立てる各サービス({@code CreateRecordService}等)では、やむを得ず文字列連結で
 * SQLへ埋め込みます。この際、不正な値がそのままSQLへ混入することを防ぐため、
 * 事前に{@code TBL_DEF}に実在するテーブル名かどうかをホワイトリスト方式で検証します。
 * </p>
 */
@Service
public class TableNameValidator {

    private static final String DISTINCT_TABLE_NAME_SQL = "SELECT DISTINCT TABLE_NAME FROM TBL_DEF";

    private final RecordQueryService recordQueryService;
    private final MsgUtil msg;

    public TableNameValidator(RecordQueryService recordQueryService, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.msg = msg;
    }

    /**
     * 指定されたテーブル名が{@code TBL_DEF}に実在するテーブル名かどうかを検証します。
     *
     * @param tableName 検証対象のテーブル名
     * @throws BusinessRuleViolationException 実在しないテーブル名が指定された場合
     */
    public void validate(String tableName) {

        // TBL_DEFに実在するテーブル名の一覧と照合し、ホワイトリストに無い値は業務エラーとする
        List<LinkedHashMap<String, String>> tableNameRows = recordQueryService.select(DISTINCT_TABLE_NAME_SQL);
        boolean exists = tableNameRows.stream().anyMatch(row -> tableName.equals(row.get("TABLE_NAME")));
        if (!exists) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.invalidTableName", tableName));
        }
    }
}
