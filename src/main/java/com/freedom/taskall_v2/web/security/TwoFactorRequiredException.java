package com.freedom.taskall_v2.web.security;

import org.springframework.security.core.AuthenticationException;

/**
 * 一次認証(メールアドレス・パスワード)を通過し、二次認証(6桁パスコード)待ちであることを表す例外です。
 *
 * <p>
 * SpringSecurityの認証フローでは、一次認証を通過してもまだ「ログイン成功」として扱わないために
 * (二段階認証を必須とするため)、通常の認証成功ではなく、この専用の例外をスローして
 * {@code AuthenticationFailureHandler}側に処理を委ねます。
 * </p>
 */
public class TwoFactorRequiredException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public TwoFactorRequiredException(String message) {
        super(message);
    }
}
