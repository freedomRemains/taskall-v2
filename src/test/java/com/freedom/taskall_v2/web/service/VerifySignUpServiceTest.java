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

import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VerifySignUpServiceTest {

    @Mock
    private SignUpService signUpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private MsgUtil msgUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VerifySignUpService verifySignUpService;

    @BeforeEach
    void setUp() {
        verifySignUpService =
                new VerifySignUpService(signUpService, passwordEncoder, errMsgService, objectMapper, msgUtil);
    }

    @Test
    void pendingSignUpIdが無い場合はトップ画面へ無言で遷移すること() throws Exception {

        JsonNode result = objectMapper.readTree(
                verifySignUpService.execute("{\"sessionId\":\"session-1\",\"SIGN_UP_CODE\":\"123456\"}"));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("signUpCompleted").asBoolean()).isTrue();
    }

    @Test
    void 対象レコードが見つからない場合はトップ画面へ無言で遷移すること() throws Exception {

        when(signUpService.findById("9")).thenReturn(Optional.empty());

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("signUpCompleted").asBoolean()).isTrue();
    }

    @Test
    void セッションIDが一致しない場合はトップ画面へ無言で遷移すること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        row.put("SESSION_ID", "another-session");
        when(signUpService.findById("9")).thenReturn(Optional.of(row));

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("signUpCompleted").asBoolean()).isTrue();
    }

    @Test
    void ロック中で有効期限内の場合は確認コード入力画面へロックエラー付きでリダイレクトされること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        when(signUpService.findById("9")).thenReturn(Optional.of(row));
        when(signUpService.isLocked(row)).thenReturn(true);
        when(signUpService.isExpired(row)).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUpPasscode.html?errMsgKey=402");
        assertThat(result.path("signUpCompleted").asBoolean()).isFalse();
    }

    @Test
    void 未ロックだが有効期限切れの場合はロック状態へ更新してロックエラーを返すこと() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        when(signUpService.findById("9")).thenReturn(Optional.of(row));
        when(signUpService.isLocked(row)).thenReturn(false);
        when(signUpService.isExpired(row)).thenReturn(true);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000402")).thenReturn("402");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "123456"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUpPasscode.html?errMsgKey=402");
        verify(signUpService).lock("9");
    }

    @Test
    void 既にアカウントが作成済みの場合は行を削除しメール既存エラーを返すこと() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        LinkedHashMap<String, String> account = new LinkedHashMap<>();
        when(signUpService.findById("9")).thenReturn(Optional.of(row));
        when(signUpService.isLocked(row)).thenReturn(false);
        when(signUpService.isExpired(row)).thenReturn(false);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.of(account));
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000409")).thenReturn("409");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUpPasscode.html?errMsgKey=409");
        verify(signUpService).deleteById("9");
        verify(signUpService, never()).createAccount(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 確認コードが間違っている場合は失敗回数を加算し再入力エラーを返すこと() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        row.put("FAIL_CNT", "1");
        when(signUpService.findById("9")).thenReturn(Optional.of(row));
        when(signUpService.isLocked(row)).thenReturn(false);
        when(signUpService.isExpired(row)).thenReturn(false);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("999999", "passcode-hash")).thenReturn(false);
        when(signUpService.recordFailureAndLockIfNeeded("9", 1)).thenReturn(false);
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000405")).thenReturn("405");

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "999999"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString())
                .isEqualTo("/taskall-v2/service/signUpPasscode.html?errMsgKey=405");
        verify(signUpService).recordFailureAndLockIfNeeded("9", 1);
        verify(signUpService, never()).createAccount(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 確認コードが一致する場合はアカウントを作成しトップ画面へ遷移すること() throws Exception {

        LinkedHashMap<String, String> row = baseRow();
        when(signUpService.findById("9")).thenReturn(Optional.of(row));
        when(signUpService.isLocked(row)).thenReturn(false);
        when(signUpService.isExpired(row)).thenReturn(false);
        when(signUpService.findAccountByMailAddress("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("042817", "passcode-hash")).thenReturn(true);

        String contextJson = objectMapper.writeValueAsString(java.util.Map.of(
                "sessionId", "session-1", "pendingSignUpId", "9", "SIGN_UP_CODE", "042817"));

        JsonNode result = objectMapper.readTree(verifySignUpService.execute(contextJson));

        assertThat(result.path("destination").asString()).isEqualTo("/taskall-v2/service/top.html");
        assertThat(result.path("signUpCompleted").asBoolean()).isTrue();
        verify(signUpService).createAccount("テスト太郎", "user@example.com", "password-hash", "1000101");
        verify(signUpService).deleteById("9");
    }

    private LinkedHashMap<String, String> baseRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("SIGN_UP_ID", "9");
        row.put("APROLE_ID", "1000101");
        row.put("SESSION_ID", "session-1");
        row.put("MAIL_ADDRESS", "user@example.com");
        row.put("ACCOUNT_NAME", "テスト太郎");
        row.put("PASSWORD_HASH", "password-hash");
        row.put("PASSCODE_HASH", "passcode-hash");
        row.put("FAIL_CNT", "0");
        row.put("IS_LOCKED", "0");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        return row;
    }
}
