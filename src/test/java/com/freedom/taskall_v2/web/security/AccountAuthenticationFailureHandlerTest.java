package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;

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
}
