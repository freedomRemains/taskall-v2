package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VerifyPasswordResetServiceTest {

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VerifyPasswordResetService verifyPasswordResetService;

    @BeforeEach
    void setUp() {
        verifyPasswordResetService =
                new VerifyPasswordResetService(passwordResetService, passwordEncoder, errMsgService, objectMapper, msgUtil);
    }

    @Test
    void pendingPasswordResetIdが無い場合はトップ画面へ無言で遷移すること() throws Exception {

        JsonNode result = objectMapper.readTree(
                verifyPasswordResetService.execute("{\"sessionId\":\"session-1\",\"PASSWORD_RESET_CODE\":\"123456\"}"));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("passwordResetCompleted").asBoolean()).isTrue();
    }

    @Test
    void 対象レコードが見つからない場合はトップ画面へ無言で遷移すること() throws Exception {

        when(passwordResetService.findById("9")).thenReturn(Optional.empty());

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("passwordResetCompleted").asBoolean()).isTrue();
    }

    @Test
    void セッションIDが一致しない場合はトップ画面へ無言で遷移すること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        row.put("SESSION_ID", "another-session");
        when(passwordResetService.findById("9")).thenReturn(Optional.of(row));

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("passwordResetCompleted").asBoolean()).isTrue();
    }

    @Test
    void ロック中で有効期限内の場合は確認コード入力画面へロックエラー付きでリダイレクトされること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        when(passwordResetService.findById("9")).thenReturn(Optional.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(true);
        when(passwordResetService.isExpired(row)).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/resetPasscode.html?errMsgKey=402");
        assertThat(result.path("passwordResetCompleted").asBoolean()).isFalse();
    }

    @Test
    void 未ロックだが有効期限切れの場合はロック状態へ更新してロックエラーを返すこと() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        when(passwordResetService.findById("9")).thenReturn(Optional.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(false);
        when(passwordResetService.isExpired(row)).thenReturn(true);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/resetPasscode.html?errMsgKey=402");
        verify(passwordResetService).lock("9");
    }

    @Test
    void メールアドレス不在でも6桁一致判定を行った上で失敗回数は1回だけ加算されること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        row.put("FAIL_CNT", "4");
        when(passwordResetService.findById("9")).thenReturn(Optional.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(false);
        when(passwordResetService.isExpired(row)).thenReturn(false);
        when(passwordResetService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("042817", "passcode-hash")).thenReturn(true);
        when(passwordResetService.recordFailureAndLockIfNeeded("9", 4)).thenReturn(true);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/resetPasscode.html?errMsgKey=402");
        verify(passwordEncoder).matches("042817", "passcode-hash");
        verify(passwordResetService).recordFailureAndLockIfNeeded("9", 4);
        verify(passwordResetService, never()).updateAccountPassword(anyString(), anyString());
    }

    @Test
    void アカウントが存在し確認コードも一致する場合はパスワード更新後にトップ画面へ遷移すること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        LinkedHashMap<String, String> account = new LinkedHashMap<>();
        account.put("ACCNT_ID", "1000101");
        when(passwordResetService.findById("9")).thenReturn(Optional.of(row));
        when(passwordResetService.isLocked(row)).thenReturn(false);
        when(passwordResetService.isExpired(row)).thenReturn(false);
        when(passwordResetService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("042817", "passcode-hash")).thenReturn(true);

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingPasswordResetId", "9", "PASSWORD_RESET_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifyPasswordResetService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("passwordResetCompleted").asBoolean()).isTrue();
        verify(passwordResetService).updateAccountPassword("1000101", "after-password-hash");
        verify(passwordResetService).deleteById("9");
    }

    private LinkedHashMap<String, String> baseRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("PASSWORD_RESET_ID", "9");
        row.put("SESSION_ID", "session-1");
        row.put("MAIL_ADDRESS", "user@example.com");
        row.put("AFTER_PASSWORD_HASH", "after-password-hash");
        row.put("PASSCODE_HASH", "passcode-hash");
        row.put("FAIL_CNT", "0");
        row.put("IS_LOCKED", "0");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        return row;
    }
}
