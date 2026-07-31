package com.freedom.taskall_v2.web.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 二段階認証で使用する6桁のランダムなパスコードを生成するユーティリティです。
 *
 * <p>
 * 推測されにくい乱数を生成するため{@link SecureRandom}を使用します。静的utilではなく
 * {@code @Component}とするのは、テスト時にモック化してパスコードを固定できるようにするためです。
 * </p>
 */
@Component
public class PasscodeGenerator {

    private static final int PASSCODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 000000〜999999のいずれかの6桁ゼロ埋め数字文字列を生成します。
     *
     * @return 6桁のパスコード文字列
     */
    public String generate() {
        int value = secureRandom.nextInt(PASSCODE_BOUND);
        return String.format("%06d", value);
    }
}
