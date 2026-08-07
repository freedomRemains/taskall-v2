package com.freedom.taskall_v2.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CredentialInitPropertiesがcustom-[環境名].yml(実行時はcustom-local.yml)の
 * 「taskall.credential-init」設定を正しくバインドできることを確認するテスト。
 *
 * ローカルプロファイルでは明示的に無効化(enabled=false)しているため、AWS認証情報が
 * 無い本テスト実行環境でもコンテキストが問題なく起動することも合わせて確認する
 * (SsmClientConfig/AwsSsmParameterFetcher/DefaultAccountCredentialInitializerは
 * enabled=trueの場合のみBean登録されるため、ここではBean自体が生成されない)。
 */
@SpringBootTest
class CredentialInitPropertiesTest {

    @Autowired
    private CredentialInitProperties credentialInitProperties;

    @Test
    void ローカル環境ではenabledがfalseにバインドされていること() {
        assertThat(credentialInitProperties.isEnabled()).isFalse();
    }

    @Test
    void parameterPrefixのデフォルト値が設定されていること() {
        assertThat(credentialInitProperties.getParameterPrefix()).isEqualTo("/taskall-v2/accnt");
    }
}
