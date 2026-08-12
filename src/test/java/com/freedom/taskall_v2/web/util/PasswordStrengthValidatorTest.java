package com.freedom.taskall_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordStrengthValidatorTest {

    private final PasswordStrengthValidator passwordStrengthValidator = new PasswordStrengthValidator();

    @Test
    void 数字英大文字英小文字記号を含む8文字以上のパスワードは有効と判定されること() {

        assertThat(passwordStrengthValidator.isValid("Abcd123!")).isTrue();
    }

    @Test
    void 条件を1つでも満たさないパスワードは無効と判定されること() {

        assertThat(passwordStrengthValidator.isValid("Abc123")).isFalse();
        assertThat(passwordStrengthValidator.isValid("abcd123!")).isFalse();
        assertThat(passwordStrengthValidator.isValid("ABCD123!")).isFalse();
        assertThat(passwordStrengthValidator.isValid("Abcdefg!")).isFalse();
        assertThat(passwordStrengthValidator.isValid("Abcd1234")).isFalse();
    }
}
