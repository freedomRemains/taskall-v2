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

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LoginService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS, A.PASSWORD,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.MAIL_ADDRESS = ?
            """;

    @Mock
    private com.freedom.taskall_v2.common.db.RecordQueryService recordQueryService;

    @Mock
    private ErrMsgService errMsgService;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(recordQueryService, errMsgService, JsonMapper.builder().build(),
                new MsgUtil());
    }

    private LinkedHashMap<String, String> accountRow(String accntId, String password) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", accntId);
        row.put("PASSWORD", password);
        return row;
    }

    @Test
    void 認証成功時はaccountIdのみを出力しrespKindを設定しないこと() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("grandmaster@account.com"))))
                .thenReturn(new ArrayList<>(List.of(accountRow("1000401", "password"))));

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"grandmaster@account.com\",\"PASSWORD\":\"password\","
                        + "\"sessionId\":\"session-1\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("accountId").asString()).isEqualTo("1000401");
        assertThat(node.has("respKind")).isFalse();
    }

    @Test
    void パスワード不一致の場合はrespKindにredirectとerrMsgKey付きdestinationが設定されること() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("grandmaster@account.com"))))
                .thenReturn(new ArrayList<>(List.of(accountRow("1000401", "password"))));
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000401")).thenReturn("5");

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"grandmaster@account.com\",\"PASSWORD\":\"wrong\","
                        + "\"sessionId\":\"session-1\",\"accountId\":\"1000001\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
        assertThat(node.path("destination").asString()).isEqualTo("myPage.html?errMsgKey=5");
    }

    @Test
    void 該当メールアドレスが存在しない場合もログイン失敗として扱われること() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("unknown@account.com"))))
                .thenReturn(new ArrayList<>());
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000401")).thenReturn("0");

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"unknown@account.com\",\"PASSWORD\":\"password\","
                        + "\"sessionId\":\"session-1\",\"accountId\":\"1000001\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
    }

    @Test
    void MAIL_ADDRESSが未指定の場合は業務例外がスローされること() {

        assertThatThrownBy(() -> loginService.execute("{\"PASSWORD\":\"password\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
