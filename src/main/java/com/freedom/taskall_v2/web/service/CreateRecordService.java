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
 * テーブルデータメンテナンス画面の「新規レコード追加」から、DBレコードを1件作成するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.CreateRecordService}に相当します。
 * サロゲートキー({@code <テーブル物理名>_ID})は本サービスが{@code MAX(...)+1}で採番し、
 * {@code VERSION}/{@code IS_DELETED}/{@code CREATED_BY}/{@code CREATED_AT}/{@code UPDATED_BY}/
 * {@code UPDATED_AT}の共通カラムも本サービスが設定するため、画面からの入力対象外です。
 * </p>
 */
@Service
public class CreateRecordService implements ScriptElementService {

    /** 共通カラム(サロゲートキー以外)のうち、画面入力の対象外とするカラム名 */
    private static final Set<String> STANDARD_COLUMNS =
            Set.of("VERSION", "IS_DELETED", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIELD_NAME_SQL =
            "SELECT FIELD_NAME FROM TBL_DEF WHERE TABLE_NAME = ? ORDER BY TBL_DEF_ID";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final TableNameValidator tableNameValidator;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public CreateRecordService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate,
            TableNameValidator tableNameValidator, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
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
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "tableName"));
        }

        // テーブル名がTBL_DEFに実在しない場合はSQLへ混入させず業務エラーとする
        tableNameValidator.validate(tableName);

        return writeAsString(doCreateRecord(context, tableName));
    }

    private ObjectNode doCreateRecord(ObjectNode context, String tableName) {

        // テーブル定義から画面入力対象のカラム(サロゲートキー・共通カラムを除く)を取得する
        List<LinkedHashMap<String, String>> fieldDefs = recordQueryService.select(FIELD_NAME_SQL, List.of(tableName));
        String surrogateKey = tableName + "_ID";
        String accountId = context.path("accountId").asString("");
        String dateString = LocalDateTime.now().format(DATE_FORMAT);
        String recordId = getNextId(tableName, surrogateKey);

        // INSERT文のカラム名・値を、テーブル定義の並び順のまま組み立てる
        StringBuilder columns = new StringBuilder(surrogateKey);
        StringBuilder placeholders = new StringBuilder("?");
        List<Object> params = new ArrayList<>();
        params.add(recordId);

        for (LinkedHashMap<String, String> fieldDef : fieldDefs) {
            String fieldName = fieldDef.get("FIELD_NAME");
            if (fieldName.equals(surrogateKey) || STANDARD_COLUMNS.contains(fieldName)) {
                continue;
            }
            columns.append(", ").append(fieldName);
            placeholders.append(", ?");
            params.add(context.path(fieldName).asString(""));
        }

        columns.append(", VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT");
        placeholders.append(", ?, ?, ?, ?, ?, ?");
        params.add("1");
        params.add("0");
        params.add(accountId);
        params.add(dateString);
        params.add(accountId);
        params.add(dateString);

        String sql = "INSERT INTO " + tableName + "(" + columns + ") VALUES(" + placeholders + ")";
        int updateCnt = jdbcTemplate.update(sql, params.toArray());

        ObjectNode output = objectMapper.createObjectNode();
        output.put("tableName", tableName);
        output.put("recordId", recordId);
        output.put("updateCnt", updateCnt);
        return output;
    }

    private String getNextId(String tableName, String surrogateKey) {

        // サロゲートキーの最大値を取得し、その次の値を新規レコードのIDとする
        List<LinkedHashMap<String, String>> maxIdRows =
                recordQueryService.select("SELECT MAX(" + surrogateKey + ") AS MAX_ID FROM " + tableName);
        String maxId = maxIdRows.get(0).get("MAX_ID");
        return maxId == null ? "1" : Integer.toString(Integer.parseInt(maxId) + 1);
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
