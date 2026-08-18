package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RecaptchaVerificationScriptElementServiceTest {

    @Mock
    private RecaptchaVerificationService recaptchaVerificationService;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecaptchaVerificationScriptElementService service;

    @BeforeEach
    void setUp() {
        service = new RecaptchaVerificationScriptElementService(recaptchaVerificationService, errMsgService,
                objectMapper, msgUtil);
    }

    @Test
    void recaptchaパラメータが無い場合は業務エラーとなること() {

        when(msgUtil.get("msg.warn.web.requiredParamMissing", "g-recaptcha-response"))
                .thenReturn("Required parameter missing: g-recaptcha-response");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class,
                () -> service.execute("{\"sessionId\":\"session-1\",\"requestUri\":\"/taskall-v2/service/signUp.html\"}"));
    }

    @Test
    void 検証失敗時はサインアップ画面へエラー付きでリダイレクトすること() throws Exception {

        when(recaptchaVerificationService.verify("token")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000410")).thenReturn("410");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "requestUri", "/taskall-v2/service/signUp.html",
                "g-recaptcha-response", "token"));

        JsonNode result = objectMapper.readTree(service.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUp.html?errMsgKey=410");
    }

    @Test
    void 検証失敗時はパスワード再設定画面へエラー付きでリダイレクトすること() throws Exception {

        when(recaptchaVerificationService.verify("token")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000410")).thenReturn("410");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "requestUri", "/taskall-v2/service/inputMail.html",
                "g-recaptcha-response", "token"));

        JsonNode result = objectMapper.readTree(service.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/inputMail.html?errMsgKey=410");
    }

    @Test
    void 検証成功時は入力コンテキストをそのまま返すこと() {

        when(recaptchaVerificationService.verify("token")).thenReturn(true);
        String contextJson = "{\"sessionId\":\"session-1\",\"requestUri\":\"/taskall-v2/service/signUp.html\","
                + "\"g-recaptcha-response\":\"token\",\"MAIL_ADDRESS\":\"user@example.com\"}";

        assertThat(service.execute(contextJson)).isEqualTo(contextJson);
        verify(recaptchaVerificationService).verify("token");
    }
}
