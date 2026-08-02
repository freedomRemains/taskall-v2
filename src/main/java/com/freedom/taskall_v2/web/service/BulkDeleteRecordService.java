package com.freedom.taskall_v2.web.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * テーブルデータメンテナンス画面の「一括削除」から、チェックボックスで選択された複数のDBレコードを
 * 削除するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.BulkDeleteRecordService}に相当します。
 * 一覧画面のチェックボックスは、レコードIDをそのままパラメータ名とし、選択時の値が{@code on}になる
 * ため、入力JSONのキーを走査して削除対象のレコードIDを判定します。
 * </p>
 */
@Service
public class BulkDeleteRecordService implements ScriptElementService {

    /** チェックボックスが選択状態のときにブラウザから送信される値 */
    private static final String CHECKED_VALUE = "on";

    private final JdbcTemplate jdbcTemplate;
    private final TableNameValidator tableNameValidator;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public BulkDeleteRecordService(JdbcTemplate jdbcTemplate, TableNameValidator tableNameValidator,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.jdbcTemplate = jdbcTemplate;
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
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", "tableName"));
        }

        // テーブル名がTBL_DEFに実在しない場合はSQLへ混入させず業務エラーとする
        tableNameValidator.validate(tableName);

        return writeAsString(doBulkDeleteRecord(context, tableName));
    }

    private ObjectNode doBulkDeleteRecord(ObjectNode context, String tableName) {

        String sql = "DELETE FROM " + tableName + " WHERE " + tableName + "_ID = ?";

        // チェックボックスがONになっている入力パラメータを、削除対象のレコードIDとみなして処理する
        List<String> deletedRecordIdList = new ArrayList<>();
        int updateCnt = 0;
        for (Iterator<String> names = context.propertyNames().iterator(); names.hasNext();) {
            String key = names.next();
            if (CHECKED_VALUE.equals(context.path(key).asString(""))) {
                updateCnt += jdbcTemplate.update(sql, key);
                deletedRecordIdList.add(key);
            }
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("tableName", tableName);
        ArrayNode recordIdArray = objectMapper.createArrayNode();
        deletedRecordIdList.forEach(recordIdArray::add);
        output.set("recordId", recordIdArray);
        output.put("updateCnt", updateCnt);
        return output;
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
