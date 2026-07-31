package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

import com.freedom.taskall_v2.web.service.ErrMsgService;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationFailureHandlerTest {

    @Mock
    private ErrMsgService errMsgService;

    @Test
    void セッションにアカウントIDが無い場合はゲストアカウントでエラーメッセージキーを取得しリダイレクトすること()
            throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        String sessionId = request.getSession().getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new BadCredentialsException("認証失敗");

        when(errMsgService.getErrMsgKey(eq(sessionId), eq("1000001"), eq("1000401"))).thenReturn("5");

        AccountAuthenticationFailureHandler handler = new AccountAuthenticationFailureHandler(errMsgService);
        handler.onAuthenticationFailure(request, response, exception);

        verify(errMsgService).getErrMsgKey(sessionId, "1000001", "1000401");
        assertThat(response.getRedirectedUrl()).isEqualTo("myPage.html?errMsgKey=5");
    }

    @Test
    void セッションに既存のアカウントIDがある場合はそのIDでエラーメッセージキーを取得すること() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("accountId", "1000101");
        String sessionId = request.getSession().getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new BadCredentialsException("認証失敗");

        when(errMsgService.getErrMsgKey(eq(sessionId), eq("1000101"), eq("1000401"))).thenReturn("7");

        AccountAuthenticationFailureHandler handler = new AccountAuthenticationFailureHandler(errMsgService);
        handler.onAuthenticationFailure(request, response, exception);

        verify(errMsgService).getErrMsgKey(sessionId, "1000101", "1000401");
        assertThat(response.getRedirectedUrl()).isEqualTo("myPage.html?errMsgKey=7");
    }

    @Test
    void ロック中の認証失敗はアカウントロックエラー付きでマイページへリダイレクトされること() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        String sessionId = request.getSession().getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new LockedException("アカウントは現在、ロックされています。");

        when(errMsgService.getErrMsgKey(any(), any(), eq("1000402"))).thenReturn("222");

        AccountAuthenticationFailureHandler handler = new AccountAuthenticationFailureHandler(errMsgService);
        handler.onAuthenticationFailure(request, response, exception);

        verify(errMsgService).getErrMsgKey(eq(sessionId), eq("1000001"), eq("1000402"));
        assertThat(response.getRedirectedUrl()).isEqualTo("myPage.html?errMsgKey=222");
    }

    @Test
    void 二段階認証待ちの例外は二段階認証画面へエラーメッセージ無しでリダイレクトされること() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new TwoFactorRequiredException("一次認証を通過しました。");

        AccountAuthenticationFailureHandler handler = new AccountAuthenticationFailureHandler(errMsgService);
        handler.onAuthenticationFailure(request, response, exception);

        verify(errMsgService, never()).getErrMsgKey(any(), any(), any());
        assertThat(response.getRedirectedUrl()).isEqualTo("/taskall-v2/service/twoFactorAuth.html");
    }
}
