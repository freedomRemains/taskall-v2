package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

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
class StartSignUpServiceTest {

    @Mock
    private SignUpService signUpService;

    @Mock
    private PasswordStrengthValidator passwordStrengthValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasscodeGenerator passcodeGenerator;

    @Mock
    private SignUpMailService signUpMailService;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StartSignUpService startSignUpService;

    @BeforeEach
    void setUp() {
        startSignUpService = new StartSignUpService(signUpService, passwordStrengthValidator, passwordEncoder,
                passcodeGenerator, signUpMailService, errMsgService, objectMapper, msgUtil);
    }

    @Test
    void MAIL_ADDRESSが無い場合は業務エラーとなること() {

        when(msgUtil.get("msg.warn.web.requiredParamMissing", "MAIL_ADDRESS"))
                .thenReturn("Required parameter missing: MAIL_ADDRESS");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class,
                () -> startSignUpService.execute("{\"sessionId\":\"session-1\"}"));
    }

    @Test
    void ACCOUNT_KINDが改ざんされている場合はエラーを出さずTOPへ遷移すること() throws Exception {

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "ACCOUNT_NAME", "テスト太郎",
                "PASSWORD", "Abcd123!",
                "PASSWORD_CONFIRM", "Abcd123!",
                "ACCOUNT_KIND", "9"));

        JsonNode result = objectMapper.readTree(startSignUpService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        verify(signUpService, never()).findByMailAddress(anyString());
    }

    @Test
    void 確認用パスワードが一致しない場合はサインアップ画面へエラー付きでリダイレクトされること() throws Exception {

        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000406")).thenReturn("406");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "ACCOUNT_NAME", "テスト太郎",
                "PASSWORD", "Abcd123!",
                "PASSWORD_CONFIRM", "Xbcd123!",
                "ACCOUNT_KIND", "1"));

        JsonNode result = objectMapper.readTree(startSignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUp.html?errMsgKey=406");
        verify(signUpService, never()).findByMailAddress(anyString());
    }

    @Test
    void 既存アカウントが存在する場合はマイページへエラー付きでリダイレクトされること() throws Exception {

        LinkedHashMap<String, String> account = new LinkedHashMap<>();
        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.of(account));
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000408")).thenReturn("408");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "ACCOUNT_NAME", "テスト太郎",
                "PASSWORD", "Abcd123!",
                "PASSWORD_CONFIRM", "Abcd123!",
                "ACCOUNT_KIND", "1"));

        JsonNode result = objectMapper.readTree(startSignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/myPage.html?errMsgKey=408");
        verify(signUpService, never()).create(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString());
    }

    @Test
    void ロック中の既存レコードがある場合はサインアップ画面へロックエラー付きでリダイレクトされること() throws Exception {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(signUpService.findByMailAddress("user@example.com")).thenReturn(List.of(row));
        when(signUpService.isLocked(row)).thenReturn(true);
        when(signUpService.isExpired(row)).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "user@example.com",
                "ACCOUNT_NAME", "テスト太郎",
                "PASSWORD", "Abcd123!",
                "PASSWORD_CONFIRM", "Abcd123!",
                "ACCOUNT_KIND", "1"));

        JsonNode result = objectMapper.readTree(startSignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUp.html?errMsgKey=402");
        verify(signUpService, never()).create(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString());
    }

    @Test
    void 正常系ではSIGN_UP登録とメール送信を行い6桁コード入力画面へ遷移すること() throws Exception {

        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(signUpService.findByMailAddress("user@example.com")).thenReturn(List.of());
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("Abcd123!")).thenReturn("password-hash");
        when(passwordEncoder.encode("042817")).thenReturn("code-hash");
        when(signUpService.create("session-1", "1000201", "user@example.com", "テスト法人", "password-hash",
                "code-hash")).thenReturn("9");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1",
                "MAIL_ADDRESS", "User@Example.com",
                "ACCOUNT_NAME", "テスト法人",
                "PASSWORD", "Abcd123!",
                "PASSWORD_CONFIRM", "Abcd123!",
                "ACCOUNT_KIND", "2"));

        JsonNode result = objectMapper.readTree(startSignUpService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/signUpPasscode.html");
        assertThat(result.path("pendingSignUpId").asString()).isEqualTo("9");
        verify(signUpMailService).sendPasscode("user@example.com", "042817");
    }

    @Test
    void メール送信失敗時は作成済みレコードを削除して例外を再スローすること() {

        when(passwordStrengthValidator.isValid("Abcd123!")).thenReturn(true);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(signUpService.findByMailAddress("user@example.com")).thenReturn(List.of());
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("Abcd123!")).thenReturn("password-hash");
        when(passwordEncoder.encode("042817")).thenReturn("code-hash");
        when(signUpService.create("session-1", "1000101", "user@example.com", "テスト太郎", "password-hash",
                "code-hash")).thenReturn("9");
        org.mockito.Mockito.doThrow(new ApplicationInternalException("mail error"))
                .when(signUpMailService).sendPasscode("user@example.com", "042817");

        String contextJson = "{\"sessionId\":\"session-1\",\"MAIL_ADDRESS\":\"user@example.com\","
                + "\"ACCOUNT_NAME\":\"テスト太郎\",\"PASSWORD\":\"Abcd123!\",\"PASSWORD_CONFIRM\":\"Abcd123!\","
                + "\"ACCOUNT_KIND\":\"1\"}";

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationInternalException.class,
                () -> startSignUpService.execute(contextJson));

        verify(signUpService).deleteById("9");
    }
}
