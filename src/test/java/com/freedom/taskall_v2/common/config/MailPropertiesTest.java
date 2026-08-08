package com.freedom.taskall_v2.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MailPropertiesがcustom-[環境名].yml(実行時はcustom-local.yml)の
 * 「taskall.mail」設定を正しくバインドできることを確認するテスト。
 */
@SpringBootTest
class MailPropertiesTest {

    @Autowired
    private MailProperties mailProperties;

    @Test
    void fromAddressのデフォルト値が設定されていること() {
        assertThat(mailProperties.getFromAddress()).isEqualTo("no-reply@taskall-v2.com");
    }
}
