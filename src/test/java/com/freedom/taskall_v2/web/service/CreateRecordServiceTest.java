package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CreateRecordService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class CreateRecordServiceTest {

    private static final String FIELD_NAME_SQL =
            "SELECT FIELD_NAME FROM TBL_DEF WHERE TABLE_NAME = ? ORDER BY TBL_DEF_ID";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TableNameValidator tableNameValidator;

    private CreateRecordService createRecordService;

    @BeforeEach
    void setUp() {
        createRecordService = new CreateRecordService(recordQueryService, jdbcTemplate, tableNameValidator,
                JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> fieldDef(String fieldName) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("FIELD_NAME", fieldName);
        return row;
    }

    @Test
    void 新規レコードが採番されたIDでINSERTされること() {

        when(recordQueryService.select(eq(FIELD_NAME_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(List.of(
                        fieldDef("ACCNT_ID"), fieldDef("ACCOUNT_NAME"), fieldDef("MAIL_ADDRESS"),
                        fieldDef("VERSION"), fieldDef("IS_DELETED"), fieldDef("CREATED_BY"),
                        fieldDef("CREATED_AT"), fieldDef("UPDATED_BY"), fieldDef("UPDATED_AT"))));

        LinkedHashMap<String, String> maxIdRow = new LinkedHashMap<>();
        maxIdRow.put("MAX_ID", "1000005");
        when(recordQueryService.select(eq("SELECT MAX(ACCNT_ID) AS MAX_ID FROM ACCNT")))
                .thenReturn(new ArrayList<>(List.of(maxIdRow)));

        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        String contextJson = """
                {"tableName":"ACCNT","accountId":"1000001","ACCOUNT_NAME":"テスト太郎","MAIL_ADDRESS":"a@example.com"}
                """;

        String result = createRecordService.execute(contextJson);

        assertThat(result).contains("\"tableName\":\"ACCNT\"");
        assertThat(result).contains("\"recordId\":\"1000006\"");
        assertThat(result).contains("\"updateCnt\":1");
        verify(jdbcTemplate).update(
                eq("INSERT INTO ACCNT(ACCNT_ID, ACCOUNT_NAME, MAIL_ADDRESS, VERSION, IS_DELETED, CREATED_BY, "
                        + "CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(Object[].class));
    }

    @Test
    void 既存レコードが無い場合はIDが1から採番されること() {

        when(recordQueryService.select(eq(FIELD_NAME_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(List.of(fieldDef("ACCNT_ID"))));

        LinkedHashMap<String, String> maxIdRow = new LinkedHashMap<>();
        maxIdRow.put("MAX_ID", null);
        when(recordQueryService.select(eq("SELECT MAX(ACCNT_ID) AS MAX_ID FROM ACCNT")))
                .thenReturn(new ArrayList<>(List.of(maxIdRow)));

        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        String result = createRecordService.execute("{\"tableName\":\"ACCNT\",\"accountId\":\"1000001\"}");

        assertThat(result).contains("\"recordId\":\"1\"");
    }

    @Test
    void tableNameが未入力の場合は業務エラーとなること() {

        assertThatThrownBy(() -> createRecordService.execute("{\"accountId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void tableNameが不正な場合は業務エラーとなりDBが更新されないこと() {

        doThrow(new BusinessRuleViolationException("不正なテーブル名です")).when(tableNameValidator).validate("INVALID");

        assertThatThrownBy(
                () -> createRecordService.execute("{\"tableName\":\"INVALID\",\"accountId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(jdbcTemplate);
    }
}
