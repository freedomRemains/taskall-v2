package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link DeleteRecordService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class DeleteRecordServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private TableNameValidator tableNameValidator;

    private DeleteRecordService deleteRecordService;

    @BeforeEach
    void setUp() {
        deleteRecordService = new DeleteRecordService(jdbcTemplate, errMsgService, tableNameValidator,
                JsonMapper.builder().build(), new MsgUtil());
    }

    @Test
    void 削除に成功した場合は更新件数が返却されること() {

        when(jdbcTemplate.update(eq("DELETE FROM ACCNT WHERE ACCNT_ID = ?"), eq("1000001"))).thenReturn(1);

        String result = deleteRecordService.execute("{\"tableName\":\"ACCNT\",\"recordId\":\"1000001\"}");

        assertThat(result).contains("\"updateCnt\":1");
        assertThat(result).doesNotContain("respKind");
    }

    @Test
    void 削除件数が0件の場合はレコード一覧画面へリダイレクトされること() {

        when(jdbcTemplate.update(eq("DELETE FROM ACCNT WHERE ACCNT_ID = ?"), eq("9999999"))).thenReturn(0);
        when(errMsgService.getErrMsgKey(eq(""), eq("1000001"), eq("1000301"))).thenReturn("123");

        String contextJson = """
                {"tableName":"ACCNT","recordId":"9999999","accountId":"1000001"}
                """;

        String result = deleteRecordService.execute(contextJson);

        assertThat(result).contains("\"respKind\":\"redirect\"");
        assertThat(result)
                .contains("\"destination\":\"/taskall-v2/service/tableDataMainte.html?tableName=ACCNT&errMsgKey=123\"");
    }

    @Test
    void recordIdが未入力の場合は業務エラーとなりDBが更新されないこと() {

        assertThatThrownBy(() -> deleteRecordService.execute("{\"tableName\":\"ACCNT\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void tableNameが不正な場合は業務エラーとなりDBが更新されないこと() {

        doThrow(new BusinessRuleViolationException("不正なテーブル名です")).when(tableNameValidator).validate("INVALID");

        assertThatThrownBy(() -> deleteRecordService.execute("{\"tableName\":\"INVALID\",\"recordId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(jdbcTemplate);
    }
}
