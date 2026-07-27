package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * {@link UpdateRecordService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class UpdateRecordServiceTest {

    private static final String FIELD_NAME_SQL =
            "SELECT FIELD_NAME FROM TBL_DEF WHERE TABLE_NAME = ? ORDER BY TBL_DEF_ID";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private TableNameValidator tableNameValidator;

    private UpdateRecordService updateRecordService;

    @BeforeEach
    void setUp() {
        updateRecordService = new UpdateRecordService(recordQueryService, jdbcTemplate, errMsgService,
                tableNameValidator, JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> fieldDef(String fieldName) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("FIELD_NAME", fieldName);
        return row;
    }

    @Test
    void 更新に成功した場合はVERSIONが1加算されて更新されること() {

        when(recordQueryService.select(eq(FIELD_NAME_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(List.of(
                        fieldDef("ACCNT_ID"), fieldDef("ACCOUNT_NAME"), fieldDef("VERSION"), fieldDef("IS_DELETED"),
                        fieldDef("CREATED_BY"), fieldDef("CREATED_AT"), fieldDef("UPDATED_BY"),
                        fieldDef("UPDATED_AT"))));
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        String contextJson = """
                {"tableName":"ACCNT","recordId":"1000001","accountId":"1000001","VERSION":"1",
                 "ACCOUNT_NAME":"テスト太郎"}
                """;

        String result = updateRecordService.execute(contextJson);

        assertThat(result).contains("\"updateCnt\":1");
        assertThat(result).doesNotContain("respKind");
        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT SET ACCOUNT_NAME = ?, VERSION = ?, UPDATED_BY = ?, UPDATED_AT = ? "
                        + "WHERE ACCNT_ID = ? AND VERSION = ?"),
                any(Object[].class));
    }

    @Test
    void 更新件数が0件の場合は楽観ロック衝突とみなしリダイレクト先が設定されること() {

        when(recordQueryService.select(eq(FIELD_NAME_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(List.of(fieldDef("ACCNT_ID"), fieldDef("VERSION"))));
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(0);
        when(errMsgService.getErrMsgKey(eq(""), eq("1000001"), eq("1000301"))).thenReturn("123");

        String contextJson = """
                {"tableName":"ACCNT","recordId":"1000001","accountId":"1000001","VERSION":"1"}
                """;

        String result = updateRecordService.execute(contextJson);

        assertThat(result).contains("\"respKind\":\"redirect\"");
        assertThat(result).contains(
                "\"destination\":\"/taskall-v2/service/tableDataMainte/editRecord.html?tableName=ACCNT&recordId=1000001&errMsgKey=123\"");
    }

    @Test
    void tableNameが未入力の場合は業務エラーとなりDBが更新されないこと() {

        assertThatThrownBy(() -> updateRecordService.execute("{\"recordId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void recordIdが未入力の場合は業務エラーとなること() {

        assertThatThrownBy(() -> updateRecordService.execute("{\"tableName\":\"ACCNT\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void tableNameが不正な場合は業務エラーとなりDBが更新されないこと() {

        doThrow(new BusinessRuleViolationException("不正なテーブル名です")).when(tableNameValidator).validate("INVALID");

        assertThatThrownBy(() -> updateRecordService
                .execute("{\"tableName\":\"INVALID\",\"recordId\":\"1000001\",\"VERSION\":\"1\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }
}
