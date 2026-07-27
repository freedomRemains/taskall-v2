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
 * {@link BulkDeleteRecordService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class BulkDeleteRecordServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TableNameValidator tableNameValidator;

    private BulkDeleteRecordService bulkDeleteRecordService;

    @BeforeEach
    void setUp() {
        bulkDeleteRecordService = new BulkDeleteRecordService(jdbcTemplate, tableNameValidator,
                JsonMapper.builder().build(), new MsgUtil());
    }

    @Test
    void チェックされたレコードIDのみ削除対象となること() {

        when(jdbcTemplate.update(eq("DELETE FROM ACCNT WHERE ACCNT_ID = ?"), eq("1000001"))).thenReturn(1);
        when(jdbcTemplate.update(eq("DELETE FROM ACCNT WHERE ACCNT_ID = ?"), eq("1000002"))).thenReturn(1);

        String contextJson = """
                {"tableName":"ACCNT","accountId":"1000099","limit":"10","offset":"0",
                 "1000001":"on","1000002":"on","1000003":"off"}
                """;

        String result = bulkDeleteRecordService.execute(contextJson);

        assertThat(result).contains("\"tableName\":\"ACCNT\"");
        assertThat(result).contains("\"updateCnt\":2");
        assertThat(result).contains("\"recordId\":[\"1000001\",\"1000002\"]");
    }

    @Test
    void チェックされたレコードが無い場合は何も削除されないこと() {

        String result = bulkDeleteRecordService.execute("{\"tableName\":\"ACCNT\",\"accountId\":\"1000099\"}");

        assertThat(result).contains("\"updateCnt\":0");
        assertThat(result).contains("\"recordId\":[]");
    }

    @Test
    void tableNameが未入力の場合は業務エラーとなること() {

        assertThatThrownBy(() -> bulkDeleteRecordService.execute("{\"accountId\":\"1000099\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void tableNameが不正な場合は業務エラーとなりDBが更新されないこと() {

        doThrow(new BusinessRuleViolationException("不正なテーブル名です")).when(tableNameValidator).validate("INVALID");

        assertThatThrownBy(
                () -> bulkDeleteRecordService.execute("{\"tableName\":\"INVALID\",\"1000001\":\"on\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(jdbcTemplate);
    }
}
