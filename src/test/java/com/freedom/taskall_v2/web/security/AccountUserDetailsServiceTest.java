package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.freedom.taskall_v2.common.db.RecordQueryService;

@ExtendWith(MockitoExtension.class)
class AccountUserDetailsServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    @Test
    void メールアドレスに一致するアカウントが1件の場合はAccountPrincipalが返却されること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", "1000001");
        row.put("MAIL_ADDRESS", "guest@account.com");
        row.put("PASSWORD", "$2a$10$hash");

        when(recordQueryService.select(org.mockito.ArgumentMatchers.anyString(), anyList()))
                .thenReturn(new ArrayList<>(List.of(row)));

        AccountUserDetailsService service = new AccountUserDetailsService(recordQueryService);

        UserDetails result = service.loadUserByUsername("guest@account.com");

        assertThat(result).isInstanceOf(AccountPrincipal.class);
        AccountPrincipal principal = (AccountPrincipal) result;
        assertThat(principal.getAccountId()).isEqualTo("1000001");
        assertThat(principal.getUsername()).isEqualTo("guest@account.com");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    void 該当アカウントが0件の場合はUsernameNotFoundExceptionがスローされること() {

        when(recordQueryService.select(org.mockito.ArgumentMatchers.anyString(), anyList()))
                .thenReturn(new ArrayList<>());

        AccountUserDetailsService service = new AccountUserDetailsService(recordQueryService);

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@account.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void 該当アカウントが2件以上の場合はUsernameNotFoundExceptionがスローされること() {

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", "1000001");
        row.put("MAIL_ADDRESS", "dup@account.com");
        row.put("PASSWORD", "$2a$10$hash");

        when(recordQueryService.select(org.mockito.ArgumentMatchers.anyString(), anyList()))
                .thenReturn(new ArrayList<>(List.of(row, row)));

        AccountUserDetailsService service = new AccountUserDetailsService(recordQueryService);

        assertThatThrownBy(() -> service.loadUserByUsername("dup@account.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
