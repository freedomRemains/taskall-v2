package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VerifyTwoFactorAuthServiceTest {

    @Mock
    private LoginStatusService loginStatusService;

    @Mock
    private AccntAuthLockService accntAuthLockService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VerifyTwoFactorAuthService verifyTwoFactorAuthService;

    @BeforeEach
    void setUp() {
        verifyTwoFactorAuthService = new VerifyTwoFactorAuthService(loginStatusService, accntAuthLockService,
                passwordEncoder, errMsgService, objectMapper, msgUtil);
    }

    @Test
    public void pendingTwoFactorAccountIdが無い場合は業務エラーとなること() {

        when(msgUtil.get("msg.err.web.requiredParamMissing", "pendingTwoFactorAccountId"))
                .thenReturn("Required parameter missing: pendingTwoFactorAccountId");

        String contextJson = "{\"sessionId\":\"session-1\"}";

        org.junit.jupiter.api.function.Executable executable =
                () -> verifyTwoFactorAuthService.execute(contextJson);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleViolationException.class, executable);
    }

    @Test
    public void アカウントロック中はTOP画面へアカウントロックエラー付きでリダイレクトされること() throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(true);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("222");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("respKind").asString()).isEqualTo("redirect");
        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=222");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }

    @Test
    public void LOGIN_STATUS行が検証対象として見つからない場合はTOP画面へ有効期限切れエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.empty());
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000403")).thenReturn("333");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=333");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }

    @Test
    public void パスコードが一致する場合はマイページへ遷移しaccountId配列が出力されること() throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("042817", "hashed-042817")).thenReturn(true);

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/myPage.html");
        assertThat(result.path("account").get(0).path("ACCNT_ID").asString()).isEqualTo("1000001");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
        verify(loginStatusService).deleteFor("1000001", "session-1");
        verify(accntAuthLockService).deleteForAccount("1000001");
    }

    @Test
    public void パスコードが一致せずロックに達しない場合は二段階認証画面へコードエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false, false);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("000000", "hashed-042817")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000404")).thenReturn("444");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "000000"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/twoFactorAuth.html?errMsgKey=444");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isFalse();
        verify(loginStatusService).markSecondAuthFail("9");
        verify(accntAuthLockService).recordFailure("1000001");
    }

    @Test
    public void パスコードが一致せずロックに達した場合はTOP画面へアカウントロックエラー付きでリダイレクトされること()
            throws Exception {

        when(accntAuthLockService.isLocked("1000001")).thenReturn(false, true);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "9");
        row.put("PASSCODE_HASH", "hashed-042817");
        when(loginStatusService.findForVerification("1000001", "session-1")).thenReturn(Optional.of(row));
        when(passwordEncoder.matches("000000", "hashed-042817")).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("555");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingTwoFactorAccountId", "1000001", "TWO_FACTOR_CODE", "000000"));

        JsonNode result = objectMapper.readTree(verifyTwoFactorAuthService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/top.html?errMsgKey=555");
        assertThat(result.path("twoFactorAuthCompleted").asBoolean()).isTrue();
    }
}
