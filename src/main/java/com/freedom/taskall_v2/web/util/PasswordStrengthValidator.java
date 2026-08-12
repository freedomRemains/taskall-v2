package com.freedom.taskall_v2.web.util;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * パスワード再設定時の新しいパスワード強度を検証するユーティリティです。
 */
@Component
public class PasswordStrengthValidator {

    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern UPPER_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");

    /**
     * 数字・英大文字・英小文字・記号を全て含む8文字以上の文字列のみを有効と判定します。
     */
    public boolean isValid(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            return false;
        }
        return DIGIT_PATTERN.matcher(rawPassword).matches()
                && UPPER_PATTERN.matcher(rawPassword).matches()
                && LOWER_PATTERN.matcher(rawPassword).matches()
                && SYMBOL_PATTERN.matcher(rawPassword).matches();
    }
}
