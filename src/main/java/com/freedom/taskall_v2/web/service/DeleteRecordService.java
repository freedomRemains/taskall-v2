package com.freedom.taskall_v2.web.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * テーブルデータメンテナンス画面の「レコード削除」から、DBレコードを1件削除するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.DeleteRecordService}に相当します。
 * 削除できなかった場合(対象レコードが既に存在しない等)は、例外をスローせず
 * PRG(Post/Redirect/Get)パターンによりテーブルデータメンテナンス画面へリダイレクトします。
 * これはUI遷移制御のための意図的な設計であり、通常の例外方針(業務/システム例外)の対象外とします。
 * </p>
 */
@Service
public class DeleteRecordService implements ScriptElementService {

    /** レコード削除失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String DELETE_FAILED_GNR_KEY_VAL_ID = "1000301";

    private final JdbcTemplate jdbcTemplate;
    private final ErrMsgService errMsgService;
    private final TableNameValidator tableNameValidator;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public DeleteRecordService(JdbcTemplate jdbcTemplate, ErrMsgService errMsgService,
            TableNameValidator tableNameValidator, ObjectMapper objectMapper, MsgUtil msg) {
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

        return writeAsString(doDeleteRecord(context, tableName, recordId));
    }

    private ObjectNode doDeleteRecord(ObjectNode context, String tableName, String recordId) {

        String sql = "DELETE FROM " + tableName + " WHERE " + tableName + "_ID = ?";
        int updateCnt = jdbcTemplate.update(sql, recordId);

        ObjectNode output = objectMapper.createObjectNode();
        if (updateCnt == 0) {
            // 削除対象レコードが見つからなかったとみなし、一覧画面へPRGパターンでリダイレクトする
            String sessionId = context.path("sessionId").asString("");
            String accountId = context.path("accountId").asString("");
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, DELETE_FAILED_GNR_KEY_VAL_ID);
            output.put("respKind", "redirect");
            output.put("destination",
                    "/taskall-v2/service/tableDataMainte.html?tableName=" + tableName + "&errMsgKey=" + errMsgKey);
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
