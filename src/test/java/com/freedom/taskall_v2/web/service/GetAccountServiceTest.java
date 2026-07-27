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

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link GetAccountService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class GetAccountServiceTest {

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.ACCNT_ID = ?
            """;

    private static final String AUTH_SQL = """
            SELECT
                B.HTML_PARTS_ID, B.AUTH_KIND
            FROM APROLE_IN_ACCNT A
            LEFT JOIN HTML_PARTS_IN_APROLE B ON A.APROLE_ID = B.APROLE_ID
            WHERE A.ACCNT_ID = ?
            ORDER BY B.HTML_PARTS_ID
            """;

    private static final String ROLE_RESTRICTION_SQL = """
            SELECT
                B.APROLE_ID
            FROM HTML_PAGE A
            LEFT JOIN REQUIRE_APROLE B ON A.HTML_PAGE_ID = B.HTML_PAGE_ID
            LEFT JOIN URI_PATTERN C ON A.URI_PATTERN_ID = C.URI_PATTERN_ID
            WHERE C.URI_PATTERN = ?
            GROUP BY B.APROLE_ID
            ORDER BY B.APROLE_ID
            """;

    private static final String ROLE_SQL = """
            SELECT
                A.APROLE_ID, B.ROLE_NAME
            FROM APROLE_IN_ACCNT A
            LEFT JOIN APROLE B ON A.APROLE_ID = B.APROLE_ID
            WHERE A.ACCNT_ID = ?
            ORDER BY A.APROLE_ID
            """;

    @Mock
    private RecordQueryService recordQueryService;

    private GetAccountService getAccountService;

    @BeforeEach
    void setUp() {
        getAccountService = new GetAccountService(recordQueryService, JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> accountRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", "1000001");
        row.put("ACCOUNT_NAME", "ゲスト");
        return row;
    }

    @Test
    void accountIdが未指定の場合はゲストアカウントIDが採用されること() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(accountRow())));
        when(recordQueryService.select(eq(AUTH_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());
        when(recordQueryService.select(eq(ROLE_RESTRICTION_SQL), eq(List.of("/taskall-v2/service/top.html"))))
                .thenReturn(new ArrayList<>(List.of(noRestrictionRow())));
        when(recordQueryService.select(eq(ROLE_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        String result = getAccountService.execute("{\"requestUri\":\"/taskall-v2/service/top.html\"}");

        assertThat(result).contains("\"accountId\":\"1000001\"");
        assertThat(result).contains("\"ACCOUNT_NAME\":\"ゲスト\"");
    }

    private LinkedHashMap<String, String> noRestrictionRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("APROLE_ID", null);
        return row;
    }

    @Test
    void ロール制約が存在しページの制約対象ロールをアカウントが保持している場合は正常終了すること() {

        LinkedHashMap<String, String> restrictionRow = new LinkedHashMap<>();
        restrictionRow.put("APROLE_ID", "1001101");

        LinkedHashMap<String, String> roleRow = new LinkedHashMap<>();
        roleRow.put("APROLE_ID", "1001101");
        roleRow.put("ROLE_NAME", "管理者");

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("1000002"))))
                .thenReturn(new ArrayList<>(List.of(accountRow())));
        when(recordQueryService.select(eq(AUTH_SQL), eq(List.of("1000002")))).thenReturn(new ArrayList<>());
        when(recordQueryService.select(eq(ROLE_RESTRICTION_SQL), eq(List.of("/taskall-v2/service/admin.html"))))
                .thenReturn(new ArrayList<>(List.of(restrictionRow)));
        when(recordQueryService.select(eq(ROLE_SQL), eq(List.of("1000002"))))
                .thenReturn(new ArrayList<>(List.of(roleRow)));

        String result = getAccountService.execute(
                "{\"accountId\":\"1000002\",\"requestUri\":\"/taskall-v2/service/admin.html\"}");

        assertThat(result).contains("\"accountId\":\"1000002\"");
    }

    @Test
    void ロール制約が存在しアカウントがいずれのロールも保持していない場合は例外がスローされること() {

        LinkedHashMap<String, String> restrictionRow = new LinkedHashMap<>();
        restrictionRow.put("APROLE_ID", "1001101");

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(accountRow())));
        when(recordQueryService.select(eq(AUTH_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());
        when(recordQueryService.select(eq(ROLE_RESTRICTION_SQL), eq(List.of("/taskall-v2/service/admin.html"))))
                .thenReturn(new ArrayList<>(List.of(restrictionRow)));
        when(recordQueryService.select(eq(ROLE_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> getAccountService.execute(
                "{\"requestUri\":\"/taskall-v2/service/admin.html\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
