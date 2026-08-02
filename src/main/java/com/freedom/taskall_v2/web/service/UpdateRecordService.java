package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * テーブルデータメンテナンス画面の「レコード編集」から、DBレコードを1件更新するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.UpdateRecordService}に相当します。
 * {@code VERSION}による楽観ロックを行い、他ユーザによる更新と衝突した場合(更新件数が0件の場合)は
 * 例外をスローせず、PRG(Post/Redirect/Get)パターンによりレコード編集画面へリダイレクトします。
 * これはUI遷移制御のための意図的な設計であり、通常の例外方針(業務/システム例外)の対象外とします。
 * </p>
 */
@Service
public class UpdateRecordService implements ScriptElementService {

    /** 楽観ロック衝突時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String OPTIMISTIC_LOCK_ERROR_GNR_KEY_VAL_ID = "1000301";

    /** 更新対象外(不変)のカラム名 */
    private static final Set<String> IMMUTABLE_COLUMNS = Set.of("IS_DELETED", "CREATED_BY", "CREATED_AT");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIELD_NAME_SQL =
            "SELECT FIELD_NAME FROM TBL_DEF WHERE TABLE_NAME = ? ORDER BY TBL_DEF_ID";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final ErrMsgService errMsgService;
    private final TableNameValidator tableNameValidator;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public UpdateRecordService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate,
            ErrMsgService errMsgService, TableNameValidator tableNameValidator, ObjectMapper objectMapper,
            MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
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
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", "tableName"));
        }
        String recordId = context.path("recordId").asString("");
        if (recordId.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", "recordId"));
        }

        // テーブル名がTBL_DEFに実在しない場合はSQLへ混入させず業務エラーとする
        tableNameValidator.validate(tableName);

        return writeAsString(doUpdateRecord(context, tableName, recordId));
    }

    private ObjectNode doUpdateRecord(ObjectNode context, String tableName, String recordId) {

        String surrogateKey = tableName + "_ID";
        String accountId = context.path("accountId").asString("");
        String dateString = LocalDateTime.now().format(DATE_FORMAT);
        String currentVersion = context.path("VERSION").asString("");

        // テーブル定義から更新対象カラムを取得し、UPDATE文を組み立てる
        List<LinkedHashMap<String, String>> fieldDefs = recordQueryService.select(FIELD_NAME_SQL, List.of(tableName));

        StringBuilder assignments = new StringBuilder();
        List<Object> params = new ArrayList<>();
        boolean hasVersion = false;

        for (LinkedHashMap<String, String> fieldDef : fieldDefs) {
            String fieldName = fieldDef.get("FIELD_NAME");
            if (fieldName.equals(surrogateKey) || IMMUTABLE_COLUMNS.contains(fieldName)) {
                continue;
            }

            if (assignments.length() > 0) {
                assignments.append(", ");
            }
            assignments.append(fieldName).append(" = ?");

            if ("VERSION".equals(fieldName)) {
                hasVersion = true;
                params.add(Integer.toString(Integer.parseInt(currentVersion) + 1));
            } else if ("UPDATED_BY".equals(fieldName)) {
                params.add(accountId);
            } else if ("UPDATED_AT".equals(fieldName)) {
                params.add(dateString);
            } else {
                params.add(context.path(fieldName).asString(""));
            }
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ").append(assignments)
                .append(" WHERE ").append(surrogateKey).append(" = ?");
        params.add(recordId);
        if (hasVersion) {
            sql.append(" AND VERSION = ?");
            params.add(currentVersion);
        }

        int updateCnt = jdbcTemplate.update(sql.toString(), params.toArray());

        ObjectNode output = objectMapper.createObjectNode();
        if (updateCnt == 0) {
            // 楽観ロック衝突とみなし、レコード編集画面へPRGパターンでリダイレクトする
            String sessionId = context.path("sessionId").asString("");
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, OPTIMISTIC_LOCK_ERROR_GNR_KEY_VAL_ID);
            output.put("respKind", "redirect");
            output.put("destination", "/taskall-v2/service/tableDataMainte/editRecord.html?tableName=" + tableName
                    + "&recordId=" + recordId + "&errMsgKey=" + errMsgKey);
        }

        output.put("tableName", tableName);
        output.put("recordId", recordId);
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
