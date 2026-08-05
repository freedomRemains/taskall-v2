package com.freedom.taskall_v2.web.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン成功時に、認証済みアカウントIDをセッションへ格納した上でマイページへ
 * リダイレクトする{@link org.springframework.security.web.authentication.AuthenticationSuccessHandler}です。
 *
 * <p>
 * セッション属性{@code accountId}は{@code TaskallV2Controller#storeAccountIdIfExists}が
 * GETリクエスト時に読み出す既存の仕組みと同じキーであり、ログイン後の画面はこの値をもとに
 * {@code GetAccountService}がアカウント情報を再取得します。
 * </p>
 */
@Component
public class AccountAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public AccountAuthenticationSuccessHandler() {
        super("/taskall-v2/service/myPage.html");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        request.getSession().setAttribute("accountId", principal.getAccountId());

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
