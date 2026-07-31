package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;

@ExtendWith(MockitoExtension.class)
class LoginStatusServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoginStatusService loginStatusService;

    @Test
    void 有効な行が存在しない場合はnot_auth状態で新規作成されること() {

        when(recordQueryService.select(eq("SELECT LOGIN_STATUS_ID, CURRENT_STATUS, PASSCODE_HASH, EXPIRES_AT "
                + "FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?"), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(), new ArrayList<>(List.of(insertedRow())));

        LinkedHashMap<String, String> result = loginStatusService.beginAttempt("1000001", "session-1");

        assertThat(result.get("CURRENT_STATUS")).isEqualTo("not_auth");
    }

    private LinkedHashMap<String, String> insertedRow() {
        LinkedHashMap<String, String> insertedRow = new LinkedHashMap<>();
        insertedRow.put("LOGIN_STATUS_ID", "1");
        insertedRow.put("CURRENT_STATUS", "not_auth");
        return insertedRow;
    }

    @Test
    void 検証時にステータスがfirst_auth_passでもsecond_auth_failでもない場合は空を返すこと() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "not_auth");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isEmpty();
    }

    @Test
    void 検証時にsecond_auth_fail状態は有効な検証対象として返されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "second_auth_fail");
        row.put("EXPIRES_AT", "2999-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("CURRENT_STATUS")).isEqualTo("second_auth_fail");
    }

    @Test
    void 期限切れの行は物理削除された上で空を返すこと() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("LOGIN_STATUS_ID", "1");
        row.put("CURRENT_STATUS", "first_auth_pass");
        row.put("EXPIRES_AT", "2000-01-01 00:00:00");
        when(recordQueryService.select(any(), eq(List.of("1000001", "session-1"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result =
                loginStatusService.findForVerification("1000001", "session-1");

        assertThat(result).isEmpty();
        verify(jdbcTemplate).update(eq("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?"), eq("1"));
    }

    @Test
    void 期限切れ行の一括削除は削除件数を返すこと() {

        when(jdbcTemplate.update(any(String.class), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(3);

        int deletedCount = loginStatusService.deleteExpired();

        assertThat(deletedCount).isEqualTo(3);
    }
}
