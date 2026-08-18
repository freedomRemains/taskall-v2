package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MsgUtil msg;

    @InjectMocks
    private SignUpService signUpService;

    @Test
    void 新規作成後に再取得したSIGN_UP_IDが返却されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("SIGN_UP_ID", "7");
        when(recordQueryService.select(any(), eq(List.of("session-1", "user@example.com"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        String signUpId = signUpService.create("session-1", "1000101", "user@example.com", "テスト太郎",
                "password-hash", "code-hash");

        assertThat(signUpId).isEqualTo("7");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO SIGN_UP"),
                eq("1000101"), eq("session-1"), eq("user@example.com"), eq("テスト太郎"), eq("password-hash"),
                eq("code-hash"), any(String.class), eq("session-1"), any(String.class), eq("session-1"),
                any(String.class));
    }

    @Test
    void 失敗回数加算で5回到達時はロック状態へ遷移すること() {

        boolean locked = signUpService.recordFailureAndLockIfNeeded("9", 4);

        assertThat(locked).isTrue();
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("UPDATE SIGN_UP"),
                eq(5), eq(5), any(String.class), eq("9"));
    }

    @Test
    void 失敗回数加算で上限未満ならロック状態へ遷移しないこと() {

        boolean locked = signUpService.recordFailureAndLockIfNeeded("9", 1);

        assertThat(locked).isFalse();
    }

    @Test
    void 有効期限が過去の行は期限切れと判定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("EXPIRES_AT", "2000-01-01 00:00:00");

        assertThat(signUpService.isExpired(row)).isTrue();
    }

    @Test
    void 期限切れ行の一括削除は削除件数を返すこと() {

        when(jdbcTemplate.update(any(String.class), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(2);

        int deletedCount = signUpService.deleteExpired();

        assertThat(deletedCount).isEqualTo(2);
    }

    @Test
    void アカウント作成時はACCNTとAPROLE_IN_ACCNTの両方が登録されること() {

        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Map<String, Object> generatedKey = new LinkedHashMap<>();
            generatedKey.put("ACCNT_ID", 5001);
            keyHolder.getKeyList().add(generatedKey);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        signUpService.createAccount("テスト太郎", "user@example.com", "password-hash", "1000201");

        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO APROLE_IN_ACCNT"),
                eq("5001"), eq("1000201"), any(String.class), any(String.class), any(String.class),
                any(String.class));
    }
}
