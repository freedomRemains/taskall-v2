package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.mail.MailAddrEncryptionService;
import com.freedom.taskall_v2.common.service.mail.MailboxAccessVerifier;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RegisterMailAddrInAccntServiceTest {

    @Mock
    private MailboxAccessVerifier mailboxAccessVerifier;

    @Mock
    private MailAddrEncryptionService mailAddrEncryptionService;

    @Mock
    private MailAddrInAccntService mailAddrInAccntService;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegisterMailAddrInAccntService registerMailAddrInAccntService;

    @BeforeEach
    void setUp() {
        registerMailAddrInAccntService = new RegisterMailAddrInAccntService(mailboxAccessVerifier,
                mailAddrEncryptionService, mailAddrInAccntService, errMsgService, objectMapper, msgUtil);
    }

    @Test
    void MAIL_ADDRが無い場合は業務エラーとなること() {

        when(msgUtil.get("msg.warn.web.requiredParamMissing", "MAIL_ADDR"))
                .thenReturn("Required parameter missing: MAIL_ADDR");

        String contextJson = objectMapper.writeValueAsString(
                Map.of("sessionId", "session-1", "accountId", "1000001", "PASSWORD", "pass"));

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class,
                () -> registerMailAddrInAccntService.execute(contextJson));
    }

    @Test
    void メールボックスへ接続できない場合は既存行を変更せずエラー付きで登録画面へ戻すこと() {

        when(mailboxAccessVerifier.canAccess("user@example.com", "wrongPass")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000201", "1000601")).thenReturn("601");

        String contextJson = objectMapper.writeValueAsString(Map.of(
                "sessionId", "session-1",
                "accountId", "1000201",
                "MAIL_ADDR", "user@example.com",
                "PASSWORD", "wrongPass"));

        JsonNode result = objectMapper.readTree(registerMailAddrInAccntService.execute(contextJson));

        assertThat(result.path("respKind").asString()).isEqualTo("redirect");
        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/mailAddrRegister.html?errMsgKey=601");
        verify(mailAddrInAccntService, never()).upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void メールボックスへ接続できた場合は暗号化して登録しマイページへ戻すこと() {

        when(mailboxAccessVerifier.canAccess("user@example.com", "correctPass")).thenReturn(true);
        when(mailAddrEncryptionService.encrypt("correctPass")).thenReturn("encryptedPass");

        String contextJson = objectMapper.writeValueAsString(Map.of(
                "sessionId", "session-1",
                "accountId", "1000201",
                "MAIL_ADDR", "user@example.com",
                "PASSWORD", "correctPass"));

        JsonNode result = objectMapper.readTree(registerMailAddrInAccntService.execute(contextJson));

        assertThat(result.path("respKind").asString()).isEqualTo("redirect");
        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/myPage.html");
        verify(mailAddrInAccntService).upsert("1000201", "user@example.com", "encryptedPass");
    }
}
