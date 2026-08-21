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
class MailAddrInAccntServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MailAddrInAccntService mailAddrInAccntService;

    @Test
    void 既存行が存在しない場合はINSERTすること() {

        when(recordQueryService.select(any(), eq(List.of("1000201")))).thenReturn(new ArrayList<>());

        mailAddrInAccntService.upsert("1000201", "user@example.com", "encryptedPass");

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO MAIL_ADDR_IN_ACCNT"),
                eq("1000201"), eq("user@example.com"), eq("encryptedPass"), eq("1000201"), any(String.class),
                eq("1000201"), any(String.class));
    }

    @Test
    void 既存行が存在する場合はVERSIONを加算してUPDATEすること() {

        LinkedHashMap<String, String> existing = new LinkedHashMap<>();
        existing.put("MAIL_ADDR_IN_ACCNT_ID", "1");
        existing.put("VERSION", "2");
        when(recordQueryService.select(any(), eq(List.of("1000201"))))
                .thenReturn(new ArrayList<>(List.of(existing)));

        mailAddrInAccntService.upsert("1000201", "new@example.com", "newEncryptedPass");

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("UPDATE MAIL_ADDR_IN_ACCNT"),
                eq("new@example.com"), eq("newEncryptedPass"), eq(3), eq("1000201"), any(String.class),
                eq("1000201"));
    }

    @Test
    void アカウントIDに紐づく行を取得できること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("MAIL_ADDR", "user@example.com");
        when(recordQueryService.select(any(), eq(List.of("1000201"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        Optional<LinkedHashMap<String, String>> result = mailAddrInAccntService.findByAccountId("1000201");

        assertThat(result).isPresent();
        assertThat(result.get().get("MAIL_ADDR")).isEqualTo("user@example.com");
    }
}
