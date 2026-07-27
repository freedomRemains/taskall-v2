package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * {@link TableNameValidator}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class TableNameValidatorTest {

    private static final String DISTINCT_TABLE_NAME_SQL = "SELECT DISTINCT TABLE_NAME FROM TBL_DEF";

    @Mock
    private RecordQueryService recordQueryService;

    private TableNameValidator tableNameValidator;

    @BeforeEach
    void setUp() {
        tableNameValidator = new TableNameValidator(recordQueryService, new MsgUtil());
    }

    private LinkedHashMap<String, String> tableNameRow(String tableName) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("TABLE_NAME", tableName);
        return row;
    }

    @Test
    void TBL_DEFに実在するテーブル名の場合は例外が発生しないこと() {

        when(recordQueryService.select(eq(DISTINCT_TABLE_NAME_SQL)))
                .thenReturn(new ArrayList<>(List.of(tableNameRow("ACCNT"), tableNameRow("NOTICE"))));

        assertThat(tableNameValidator).isNotNull();
        tableNameValidator.validate("ACCNT");
    }

    @Test
    void TBL_DEFに実在しないテーブル名の場合は業務エラーとなること() {

        when(recordQueryService.select(eq(DISTINCT_TABLE_NAME_SQL)))
                .thenReturn(new ArrayList<>(List.of(tableNameRow("ACCNT"), tableNameRow("NOTICE"))));

        assertThatThrownBy(() -> tableNameValidator.validate("DROP TABLE ACCNT; --"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
