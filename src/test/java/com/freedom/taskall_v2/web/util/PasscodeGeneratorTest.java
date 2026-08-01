package com.freedom.taskall_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class PasscodeGeneratorTest {

    private final PasscodeGenerator passcodeGenerator = new PasscodeGenerator();

    @RepeatedTest(20)
    void 生成される値は必ず6桁の数字文字列であること() {

        String passcode = passcodeGenerator.generate();

        assertThat(passcode).hasSize(6);
        assertThat(passcode).matches("[0-9]{6}");
    }

    @Test
    void ゼロ埋めされた小さい値も6桁で返却されること() {

        // SecureRandomの結果に依存せず、ゼロ埋め処理自体を検証するため複数回試行して0始まりの値が
        // 出現することを確認する(1000000通り中0〜99999が出現する確率は約10%であり、20回中に
        // 十分な回数出現することを期待する)
        boolean foundZeroPadded = false;
        for (int i = 0; i < 200; i++) {
            if (passcodeGenerator.generate().startsWith("0")) {
                foundZeroPadded = true;
                break;
            }
        }
        assertThat(foundZeroPadded).isTrue();
    }
}
