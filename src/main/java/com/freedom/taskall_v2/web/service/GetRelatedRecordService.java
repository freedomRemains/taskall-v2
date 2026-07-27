package com.freedom.taskall_v2.web.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * テーブルデータメンテナンス画面の「レコード編集」「レコード削除」から、主キー規則
 * ({@code <テーブル物理名>_ID})を利用して、対象レコードを外部キーとして参照している他テーブルの
 * レコード一覧を取得するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.GetRelatedRecordService}に相当します。
 * 移植元は{@code foreignTableDefList_<テーブル名>}のようなテーブル名連結キーをリクエスト属性へ
 * 動的に設定していましたが、本移植ではThymeleafから動的キーでモデル属性を参照できないため、
 * {@code relatedTableList}配列の各要素へ表示に必要な情報をネストする構造に変更しています。
 * </p>
 */
@Service
public class GetRelatedRecordService implements ScriptElementService {

    /** レコードが見つからない場合のエラーメッセージに対応する汎用キー値マスタID */
    private static final String RECORD_NOT_FOUND_GNR_KEY_VAL_ID = "1000301";

    private static final String TABLE_DEF_SQL = "SELECT * FROM TBL_DEF WHERE TABLE_NAME = ?";
    private static final String FOREIGN_TABLE_DEF_SQL = "SELECT * FROM TBL_DEF WHERE FOREIGN_TABLE = ?";

    private final RecordQueryService recordQueryService;
    private final ErrMsgService errMsgService;
    private final TableNameValidator tableNameValidator;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public GetRelatedRecordService(RecordQueryService recordQueryService, ErrMsgService errMsgService,
            TableNameValidator tableNameValidator, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.errMsgService = errMsgService;
        this.tableNameValidator = tableNameValidator;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // 必須パラメータが入力されていなければ業務エラーとする
        ObjectNode context = readAsObjectNode(contextJson);
        String tableName = context.path("tableName").asString("");
        if (tableName.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "tableName"));
        }
        String recordId = context.path("recordId").asString("");
        if (recordId.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "recordId"));
        }

        // テーブル名がTBL_DEFに実在しない場合はSQLへ混入させず業務エラーとする
        tableNameValidator.validate(tableName);

        return writeAsString(doGetRelatedRecord(context, tableName, recordId));
    }

    private ObjectNode doGetRelatedRecord(ObjectNode context, String tableName, String recordId) {

        // 処理対象のレコードが存在しない場合は、一覧画面へPRGパターンでリダイレクトする
        List<LinkedHashMap<String, String>> targetRecordRows =
                recordQueryService.select("SELECT * FROM " + tableName + " WHERE " + tableName + "_ID = ?",
                        List.of(recordId));

        ObjectNode output = objectMapper.createObjectNode();
        if (targetRecordRows.isEmpty()) {
            String sessionId = context.path("sessionId").asString("");
            String accountId = context.path("accountId").asString("");
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, RECORD_NOT_FOUND_GNR_KEY_VAL_ID);
            output.put("respKind", "redirect");
            output.put("destination",
                    "/taskall-v2/service/tableDataMainte.html?tableName=" + tableName + "&errMsgKey=" + errMsgKey);
            return output;
        }

        // 対象レコードを外部キーとして参照しているテーブル(自テーブルを除く)を取得する
        ArrayNode relatedTableList = objectMapper.createArrayNode();
        for (String relatingTableName : getRelatingTableNameList(tableName)) {

            List<LinkedHashMap<String, String>> relatingTableDefList = getTableDefList(relatingTableName);

            if (relatingTableDefList.get(0).get("DESC_FIELD") == null
                    || relatingTableDefList.get(0).get("DESC_FIELD").isBlank()) {

                // 意味を説明する項目(DESC_FIELD)を持たない場合は組み合わせテーブルとみなし、
                // もう1段先の外部テーブルまで追跡してから表示する
                traceForeignTable(relatedTableList, relatingTableName, relatingTableDefList, tableName, recordId);

            } else {

                // DESC_FIELDを持つ場合は、そのままレコードを取得して表示する
                addRelatedTable(relatedTableList, relatingTableName, relatingTableDefList,
                        relatingTableName, relatingTableName + "_ID", relatingTableDefList.get(0).get("DESC_FIELD"),
                        tableName, recordId);
            }
        }

        output.set("relatedTableList", relatedTableList);
        return output;
    }

    private List<String> getRelatingTableNameList(String tableName) {

        // 自テーブルを外部キーとして参照しているテーブル名を、重複無く取得する
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FOREIGN_TABLE_DEF_SQL, List.of(tableName));
        List<String> relatingTableNameList = new ArrayList<>();
        for (LinkedHashMap<String, String> row : rows) {
            String relatingTableName = row.get("TABLE_NAME");
            if (!relatingTableNameList.contains(relatingTableName)) {
                relatingTableNameList.add(relatingTableName);
            }
        }
        return relatingTableNameList;
    }

    private List<LinkedHashMap<String, String>> getTableDefList(String tableName) {
        return recordQueryService.select(TABLE_DEF_SQL, List.of(tableName));
    }

    private String findFieldLogicalName(List<LinkedHashMap<String, String>> tableDefList, String fieldName) {
        for (LinkedHashMap<String, String> def : tableDefList) {
            if (fieldName.equals(def.get("FIELD_NAME"))) {
                return def.get("FIELD_LOGICAL_NAME");
            }
        }
        return "";
    }

    private void traceForeignTable(ArrayNode relatedTableList, String relatingTableName,
            List<LinkedHashMap<String, String>> relatingTableDefList, String targetTableName, String recordId) {

        // 組み合わせテーブル自身のレコード(対象レコードを参照している行)を取得する
        List<LinkedHashMap<String, String>> relatingRecordList =
                getRelatingRecordList(relatingTableName, relatingTableDefList, targetTableName, recordId);

        // 組み合わせテーブルが参照している、対象テーブル以外の外部テーブルを1つずつ処理する
        for (String foreignTableName : getForeignTableNameList(relatingTableDefList, targetTableName)) {

            List<LinkedHashMap<String, String>> foreignTableDefList = getTableDefList(foreignTableName);
            String primaryKeyField = foreignTableName + "_ID";
            String descField = foreignTableDefList.get(0).get("DESC_FIELD");
            String primaryKeyFieldLogicalName = findFieldLogicalName(foreignTableDefList, primaryKeyField);
            String descFieldLogicalName = findFieldLogicalName(foreignTableDefList, descField);

            List<String> foreignIdList = new ArrayList<>();
            for (LinkedHashMap<String, String> relatingRecord : relatingRecordList) {
                String foreignId = relatingRecord.get(primaryKeyField);
                if (foreignId != null) {
                    foreignIdList.add(foreignId);
                }
            }

            ArrayNode records = objectMapper.createArrayNode();
            if (!foreignIdList.isEmpty()) {
                String sql = "SELECT " + primaryKeyField + ", " + descField + " FROM " + foreignTableName + " WHERE "
                        + primaryKeyField + " IN (" + String.join(", ", foreignIdList) + ")";
                for (LinkedHashMap<String, String> record : recordQueryService.select(sql)) {
                    ObjectNode recordNode = objectMapper.createObjectNode();
                    recordNode.put(primaryKeyField, record.get(primaryKeyField));
                    recordNode.put(descField, record.get(descField));
                    records.add(recordNode);
                }
            }

            ObjectNode relatedTable = objectMapper.createObjectNode();
            relatedTable.put("tableName", relatingTableName);
            relatedTable.put("tableLogicalName", relatingTableDefList.get(0).get("TABLE_LOGICAL_NAME"));
            relatedTable.put("primaryKeyField", primaryKeyField);
            relatedTable.put("primaryKeyFieldLogicalName", primaryKeyFieldLogicalName);
            relatedTable.put("descField", descField);
            relatedTable.put("descFieldLogicalName", descFieldLogicalName);
            relatedTable.put("foreignTableName", foreignTableName);
            relatedTable.set("records", records);
            relatedTableList.add(relatedTable);
        }
    }

    private void addRelatedTable(ArrayNode relatedTableList, String relatingTableName,
            List<LinkedHashMap<String, String>> relatingTableDefList, String recordTableName, String primaryKeyField,
            String descField, String targetTableName, String recordId) {

        List<LinkedHashMap<String, String>> recordRows =
                getRelatingRecordList(relatingTableName, relatingTableDefList, targetTableName, recordId);

        ArrayNode records = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> record : recordRows) {
            ObjectNode recordNode = objectMapper.createObjectNode();
            recordNode.put(primaryKeyField, record.get(primaryKeyField));
            recordNode.put(descField, record.get(descField));
            records.add(recordNode);
        }

        ObjectNode relatedTable = objectMapper.createObjectNode();
        relatedTable.put("tableName", relatingTableName);
        relatedTable.put("tableLogicalName", relatingTableDefList.get(0).get("TABLE_LOGICAL_NAME"));
        relatedTable.put("primaryKeyField", primaryKeyField);
        relatedTable.put("primaryKeyFieldLogicalName", findFieldLogicalName(relatingTableDefList, primaryKeyField));
        relatedTable.put("descField", descField);
        relatedTable.put("descFieldLogicalName", findFieldLogicalName(relatingTableDefList, descField));
        relatedTable.put("foreignTableName", relatingTableName);
        relatedTable.set("records", records);
        relatedTableList.add(relatedTable);
    }

    private List<LinkedHashMap<String, String>> getRelatingRecordList(String relatingTableName,
            List<LinkedHashMap<String, String>> relatingTableDefList, String targetTableName, String recordId) {

        // 対象テーブルを参照している外部キーカラム(複数あり得る)をWHERE句のOR条件にする
        List<String> foreignKeyColumnList = new ArrayList<>();
        for (LinkedHashMap<String, String> def : relatingTableDefList) {
            if (targetTableName.equals(def.get("FOREIGN_TABLE"))) {
                foreignKeyColumnList.add(def.get("FIELD_NAME"));
            }
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(relatingTableName).append(" WHERE ");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < foreignKeyColumnList.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(foreignKeyColumnList.get(i)).append(" = ?");
            params.add(recordId);
        }

        return recordQueryService.select(sql.toString(), params.stream().map(String::valueOf).toList());
    }

    private List<String> getForeignTableNameList(List<LinkedHashMap<String, String>> relatingTableDefList,
            String targetTableName) {

        // 主キー・共通カラム、及び対象テーブル自身への参照を除いた、外部キー参照先テーブル名を集める
        String relatingTableName = relatingTableDefList.get(0).get("TABLE_NAME");
        List<String> foreignTableNameList = new ArrayList<>();
        for (LinkedHashMap<String, String> def : relatingTableDefList) {
            String fieldName = def.get("FIELD_NAME");
            if (fieldName.equals(relatingTableName + "_ID") || "VERSION".equals(fieldName)
                    || "IS_DELETED".equals(fieldName) || "CREATED_BY".equals(fieldName)
                    || "CREATED_AT".equals(fieldName) || "UPDATED_BY".equals(fieldName)
                    || "UPDATED_AT".equals(fieldName)) {
                continue;
            }
            String foreignTable = def.get("FOREIGN_TABLE");
            if (foreignTable != null && !foreignTable.isBlank() && !foreignTable.equals(targetTableName)
                    && !foreignTableNameList.contains(foreignTable)) {
                foreignTableNameList.add(foreignTable);
            }
        }
        return foreignTableNameList;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
