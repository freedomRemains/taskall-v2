package com.freedom.taskall_v2.web.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.web.service.ErrMsgService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン失敗時に、PRG(Post/Redirect/Get)パターンでエラーメッセージキー付きのマイページへ
 * リダイレクトする{@link AuthenticationFailureHandler}です。
 *
 * <p>
 * 旧{@code LoginService}の認証失敗時の挙動(汎用キー値マスタ{@code 1000401}からエラーメッセージを
 * 取得し{@code ERR_MSG}へ登録した上で、そのキーをクエリパラメータへ付与してリダイレクトする)を
 * そのまま踏襲します。アカウントIDは未ログイン状態のためセッション未設定時はゲスト
 * ({@code 1000001})を用います。
 * </p>
 */
@Component
public class AccountAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** ログイン失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String LOGIN_ERROR_GNR_KEY_VAL_ID = "1000401";

    /** アカウント未特定時(未ログイン)に用いるゲストアカウントのID */
    private static final String GUEST_ACCOUNT_ID = "1000001";

    private final ErrMsgService errMsgService;

    public AccountAuthenticationFailureHandler(ErrMsgService errMsgService) {
        this.errMsgService = errMsgService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        String sessionId = request.getSession().getId();
        Object accountIdAttribute = request.getSession().getAttribute("accountId");
        String accountId = accountIdAttribute != null ? accountIdAttribute.toString() : GUEST_ACCOUNT_ID;

        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, LOGIN_ERROR_GNR_KEY_VAL_ID);

        response.sendRedirect("myPage.html?errMsgKey=" + errMsgKey);
    }
}
