package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;
import com.freedom.taskall_v2.web.util.PasswordStrengthValidator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class StartPasswordResetServiceTest {

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private PasswordStrengthValidator passwordStrengthValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasscodeGenerator passcodeGenerator;

    @Mock
    private PasswordResetMailService passwordResetMailService;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StartPasswordResetService startPasswordResetService;

    @BeforeEach
    void setUp() {
        startPasswordResetService = new StartPasswordResetService(passwordResetService, passwordStrengthValidator,
                passwordEncoder, passcodeGenerator, passwordResetMailService, errMsgService, objectMapper, msgUtil);
    }

    @Test
    void MAIL_ADDRESSが無い場合は業務エラーとなること() {

        when(msgUtil.get("msg.warn.web.requiredParamMissing", "MAIL_ADDRESS"))
                .thenReturn("Required parameter missing: MAIL_ADDRESS");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class,
                () -> startPasswordResetService.execute("{\"sessionId\":\"session-1\"}"));
    }

    @Test
    void 確認用パスワードが一致しない場合は入力画面へエラー付きでリダイレクトされること() throws Exception {

        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000406")).thenReturn("406");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "AFTER_PASSWORD", "Abcd123!",
                "AFTER_PASSWORD_CONFIRM", "Xbcd123!"));

        JsonNode result = objectMapper.readTree(startPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/inputMail.html?errMsgKey=406");
        verify(passwordResetService, never()).findByMailAddress(anyString());
    }

    @Test
    void ロック中の既存レコードがある場合は入力画面へロックエラー付きでリダイレクトされること() throws Exception {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(passwordResetService.findByMailAddress("user@example.com")).thenReturn(List.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(true);
        when(passwordResetService.isExpired(row)).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "AFTER_PASSWORD", "Abcd123!",
                "AFTER_PASSWORD_CONFIRM", "Abcd123!"));

        JsonNode result = objectMapper.readTree(startPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/inputMail.html?errMsgKey=402");
        verify(passwordResetService, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 正常系ではPASSWORD_RESET登録とメール送信を行い6桁コード入力画面へ遷移すること() throws Exception {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(passwordResetService.findByMailAddress("user@example.com")).thenReturn(List.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(false);
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("Abcd123!")).thenReturn("after-hash");
        when(passwordEncoder.encode("042817")).thenReturn("code-hash");
        when(passwordResetService.create("session-1", "user@example.com", "after-hash", "code-hash")).thenReturn("9");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "AFTER_PASSWORD", "Abcd123!",
                "AFTER_PASSWORD_CONFIRM", "Abcd123!"));

        JsonNode result = objectMapper.readTree(startPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/resetPasscode.html");
        assertThat(result.path("pendingPasswordResetId").asString()).isEqualTo("9");
        verify(passwordResetService).deleteByMailAddress("user@example.com");
        verify(passwordResetMailService).sendPasscode("user@example.com", "042817");
    }

    @Test
    void メール送信失敗時は作成済みレコードを削除して例外を再スローすること() {

        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(passwordResetService.findByMailAddress("user@example.com")).thenReturn(List.of());
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("Abcd123!")).thenReturn("after-hash");
        when(passwordEncoder.encode("042817")).thenReturn("code-hash");
        when(passwordResetService.create("session-1", "user@example.com", "after-hash", "code-hash")).thenReturn("9");
        org.mockito.Mockito.doThrow(new ApplicationInternalException("mail error"))
                .when(passwordResetMailService).sendPasscode("user@example.com", "042817");

        String contextJson = "{\"sessionId\":\"session-1\",\"MAIL_ADDRESS\":\"user@example.com\","
                + "\"AFTER_PASSWORD\":\"Abcd123!\",\"AFTER_PASSWORD_CONFIRM\":\"Abcd123!\"}";

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationInternalException.class,
                () -> startPasswordResetService.execute(contextJson));

        verify(passwordResetService).deleteById("9");
    }
}
