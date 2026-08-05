package com.freedom.taskall_v2.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

class AccountAuthenticationSuccessHandlerTest {

    @Test
    void 認証成功時にセッションへアカウントIDを格納しマイページへリダイレクトすること() throws Exception {

        AccountAuthenticationSuccessHandler handler = new AccountAuthenticationSuccessHandler();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AccountPrincipal principal = new AccountPrincipal("1000101", "gnruser@account.com", "$2a$10$hash");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(request.getSession().getAttribute("accountId")).isEqualTo("1000101");
        assertThat(response.getRedirectedUrl()).isEqualTo("/taskall-v2/service/myPage.html");
    }
}
