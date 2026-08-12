package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MsgUtil msg;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void 新規作成後に再取得したPASSWORD_RESET_IDが返却されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("PASSWORD_RESET_ID", "7");
        when(recordQueryService.select(any(), eq(List.of("session-1", "user@example.com"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        String passwordResetId =
                passwordResetService.create("session-1", "user@example.com", "after-hash", "code-hash");

        assertThat(passwordResetId).isEqualTo("7");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO PASSWORD_RESET"),
                eq("session-1"), eq("user@example.com"), eq("after-hash"), eq("code-hash"), any(String.class),
                eq("session-1"), any(String.class), eq("session-1"), any(String.class));
    }

    @Test
    void 失敗回数加算で5回到達時はロック状態へ遷移すること() {

        boolean locked = passwordResetService.recordFailureAndLockIfNeeded("9", 4);

        assertThat(locked).isTrue();
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("UPDATE PASSWORD_RESET"),
                eq(5), eq(5), any(String.class), eq("9"));
    }

    @Test
    void 失敗回数加算で上限未満ならロック状態へ遷移しないこと() {

        boolean locked = passwordResetService.recordFailureAndLockIfNeeded("9", 1);

        assertThat(locked).isFalse();
    }

    @Test
    void 有効期限が過去の行は期限切れと判定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("EXPIRES_AT", "2000-01-01 00:00:00");

        assertThat(passwordResetService.isExpired(row)).isTrue();
    }

    @Test
    void 期限切れ行の一括削除は削除件数を返すこと() {

        when(jdbcTemplate.update(any(String.class), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(2);

        int deletedCount = passwordResetService.deleteExpired();

        assertThat(deletedCount).isEqualTo(2);
    }
}
