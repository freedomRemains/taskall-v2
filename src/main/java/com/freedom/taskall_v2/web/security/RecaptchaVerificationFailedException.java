package com.freedom.taskall_v2.web.security;

import org.springframework.security.core.AuthenticationException;

/**
 * ログイン入口でreCAPTCHA検証に失敗したことを表す例外です。
 */
public class RecaptchaVerificationFailedException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public RecaptchaVerificationFailedException(String message) {
        super(message);
    }
}
