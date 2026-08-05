package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
class AccntAuthLockServiceTest {

    private static final String FIND_SQL = "SELECT ACCNT_AUTH_LOCK_ID, FAIL_CNT, LOCKED_UNTIL "
            + "FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MsgUtil msgUtil;

    @InjectMocks
    private AccntAuthLockService accntAuthLockService;

    @Test
    void 行が存在しない場合はロックされていないと判定されること() {

        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        assertThat(accntAuthLockService.isLocked("1000001")).isFalse();
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void ロック解除予定時刻が未来の場合はロック中と判定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "5");
        row.put("LOCKED_UNTIL", "2999-01-01 00:00:00");
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        assertThat(accntAuthLockService.isLocked("1000001")).isTrue();
    }

    @Test
    void ロック解除予定時刻が過去の場合はロックされていないと判定され失敗回数がリセットされること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "5");
        row.put("LOCKED_UNTIL", "2000-01-01 00:00:00");
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        assertThat(accntAuthLockService.isLocked("1000001")).isFalse();
        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?"),
                eq("1"));
    }

    @Test
    void 失敗回数が5に達するとアトミックUPDATEでロック解除予定時刻が設定されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "4");
        row.put("LOCKED_UNTIL", null);
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        accntAuthLockService.recordFailure("1000001");

        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = FAIL_CNT + 1, "
                        + "LOCKED_UNTIL = CASE WHEN FAIL_CNT + 1 >= ? THEN ? ELSE LOCKED_UNTIL END "
                        + "WHERE ACCNT_AUTH_LOCK_ID = ?"),
                eq(5), any(String.class), eq("1"));
    }

    @Test
    void 失敗回数が上限未満の場合もアトミックUPDAT文が使用されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_AUTH_LOCK_ID", "1");
        row.put("FAIL_CNT", "2");
        row.put("LOCKED_UNTIL", null);
        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        accntAuthLockService.recordFailure("1000001");

        // 上限未満でも同じSQL文が使われる（CASEのELSEブランチでLOCKED_UNTILは変化しない）
        verify(jdbcTemplate).update(
                eq("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = FAIL_CNT + 1, "
                        + "LOCKED_UNTIL = CASE WHEN FAIL_CNT + 1 >= ? THEN ? ELSE LOCKED_UNTIL END "
                        + "WHERE ACCNT_AUTH_LOCK_ID = ?"),
                eq(5), any(String.class), eq("1"));
    }

    @Test
    void 行が存在しない場合の失敗記録は新規作成のINSERT文が実行されること() {

        when(recordQueryService.select(eq(FIND_SQL), eq(List.of("1000001")))).thenReturn(new ArrayList<>());

        accntAuthLockService.recordFailure("1000001");

        verify(jdbcTemplate, times(1)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO ACCNT_AUTH_LOCK"),
                eq("1000001"), eq("1000001"), any(String.class), eq("1000001"), any(String.class));
    }
}
