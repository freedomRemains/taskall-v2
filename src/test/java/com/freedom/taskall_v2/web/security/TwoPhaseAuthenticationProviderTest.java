package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.freedom.taskall_v2.web.service.AccntAuthLockService;
import com.freedom.taskall_v2.web.service.LoginStatusService;
import com.freedom.taskall_v2.web.service.TwoFactorMailService;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;

@ExtendWith(MockitoExtension.class)
class TwoPhaseAuthenticationProviderTest {

    @Mock
    private AccountUserDetailsService accountUserDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccntAuthLockService accntAuthLockService;

    @Mock
    private LoginStatusService loginStatusService;

    @Mock
    private PasscodeGenerator passcodeGenerator;

    @Mock
    private TwoFactorMailService twoFactorMailService;

    private MockHttpServletRequest request;

    private TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.getSession(true);
        // RequestContextHolderへMockHttpServletRequestを設定する
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        twoPhaseAuthenticationProvider = new TwoPhaseAuthenticationProvider(accountUserDetailsService,
                passwordEncoder, accntAuthLockService, loginStatusService, passcodeGenerator, twoFactorMailService);
    }

    @AfterEach
    void tearDown() {
        // テスト終了後にRequestContextHolderをクリアする
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void メールアドレスに対応するアカウントが存在しない場合は他のテーブル操作なしでBadCredentialsExceptionとなること() {

        when(accountUserDetailsService.loadUserByUsername("nobody@example.com"))
                .thenThrow(new UsernameNotFoundException("nobody@example.com"));
        Authentication authentication = new UsernamePasswordAuthenticationToken("nobody@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(BadCredentialsException.class);

        verify(accntAuthLockService, org.mockito.Mockito.never()).isLocked(any());
        verify(loginStatusService, org.mockito.Mockito.never()).beginAttempt(any(), any());
    }

    @Test
    void ロック中のアカウントはLockedExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(true);
        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void パスワードが一致する場合はパスコードメールを送信しTwoFactorRequiredExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> loginStatusRow = new LinkedHashMap<>();
        loginStatusRow.put("LOGIN_STATUS_ID", "9");
        when(loginStatusService.beginAttempt("1000001", request.getSession().getId())).thenReturn(loginStatusRow);

        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(passcodeGenerator.generate()).thenReturn("042817");
        when(passwordEncoder.encode("042817")).thenReturn("hashed-042817");

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(TwoFactorRequiredException.class);

        verify(loginStatusService).markFirstAuthPass("9", "hashed-042817");
        verify(accntAuthLockService).resetFailCountOnSuccess("1000001");
        verify(twoFactorMailService).sendPasscode("user@example.com", "042817");
        assertThat(request.getSession().getAttribute("pendingTwoFactorAccountId")).isEqualTo("1000001");
    }

    @Test
    void パスワードが一致しない場合は失敗が記録されBadCredentialsExceptionとなること() {

        AccountPrincipal principal = new AccountPrincipal("1000001", "user@example.com", "hashed-password");
        when(accountUserDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(accntAuthLockService.isLocked("1000001")).thenReturn(false);

        LinkedHashMap<String, String> loginStatusRow = new LinkedHashMap<>();
        loginStatusRow.put("LOGIN_STATUS_ID", "9");
        when(loginStatusService.beginAttempt(eq("1000001"), any())).thenReturn(loginStatusRow);
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "wrong-password");

        assertThatThrownBy(() -> twoPhaseAuthenticationProvider.authenticate(authentication))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginStatusService).markFirstAuthFail("9");
        verify(accntAuthLockService).recordFailure("1000001");
    }
}
