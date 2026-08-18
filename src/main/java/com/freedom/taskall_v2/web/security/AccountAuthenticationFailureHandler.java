package com.freedom.taskall_v2.web.security;

import java.io.IOException;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.web.service.ErrMsgService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン失敗時に、PRG(Post/Redirect/Get)パターンで例外種別に応じた遷移先へ
 * リダイレクトする{@link AuthenticationFailureHandler}です。
 *
 * <p>
 * 旧{@code LoginService}の認証失敗時の挙動(汎用キー値マスタ{@code 1000401}からエラーメッセージを
 * 取得し{@code ERR_MSG}へ登録した上で、そのキーをクエリパラメータへ付与してリダイレクトする)を
 * 通常の認証失敗({@link org.springframework.security.authentication.BadCredentialsException}等)
 * には踏襲しつつ、{@link LockedException}(アカウントロック中)は専用のエラーメッセージへ、
 * {@link TwoFactorRequiredException}(一次認証通過・二次認証待ち)はエラーメッセージ無しで
 * 二段階認証画面へ、それぞれ分岐してリダイレクトします。
 * </p>
 */
@Component
public class AccountAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** ログイン失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String LOGIN_ERROR_GNR_KEY_VAL_ID = "1000401";

    /** アカウントロック中エラーメッセージに対応する汎用キー値マスタID */
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";
    private static final String RECAPTCHA_ERROR_GNR_KEY_VAL_ID = "1000410";

    /** 二段階認証画面のURI */
    private static final String TWO_FACTOR_AUTH_URI = "/taskall-v2/service/twoFactorAuth.html";

    /** アカウント未特定時(未ログイン)に用いるゲストアカウントのID */
    private static final String GUEST_ACCOUNT_ID = "1000001";

    private final ErrMsgService errMsgService;

    public AccountAuthenticationFailureHandler(ErrMsgService errMsgService) {
        this.errMsgService = errMsgService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        // 二次認証待ちの場合は、まだ認証エラーではないためエラーメッセージを発行せず
        // 二段階認証画面へそのまま遷移させる
        if (exception instanceof TwoFactorRequiredException) {
            response.sendRedirect(TWO_FACTOR_AUTH_URI);
            return;
        }

        String sessionId = request.getSession().getId();
        Object accountIdAttribute = request.getSession().getAttribute("accountId");
        String accountId = accountIdAttribute != null ? accountIdAttribute.toString() : GUEST_ACCOUNT_ID;

        // ロック中は専用のエラーメッセージ、それ以外(パスワード不一致等)は従来通りのログインエラーとする
        String gnrKeyValId =
                exception instanceof LockedException ? ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID
                        : exception instanceof RecaptchaVerificationFailedException ? RECAPTCHA_ERROR_GNR_KEY_VAL_ID
                                : LOGIN_ERROR_GNR_KEY_VAL_ID;
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, gnrKeyValId);

        response.sendRedirect("myPage.html?errMsgKey=" + errMsgKey);
    }
}
