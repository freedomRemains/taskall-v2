package com.freedom.taskall_v2.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.kms.KmsClient;

/**
 * AWS SDK for Javaの{@link KmsClient}をBean登録する設定クラスです(issue #96)。
 *
 * <p>
 * {@code taskall.mail-addr-encryption.enabled=true}の場合のみBean登録することで、
 * ローカル開発・単体テスト実行時にAWSリージョン/認証情報が未設定でもアプリ起動やテストの
 * コンテキスト読み込みに支障が出ないようにする({@link SsmClientConfig}と同じ方針)。
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "taskall.mail-addr-encryption", name = "enabled", havingValue = "true")
public class KmsClientConfig {

    /**
     * デフォルトのリージョン・認証情報プロバイダチェーンを使用する{@link KmsClient}を生成します。
     * 本番のEC2インスタンスでは、IAMインスタンスプロファイルの認証情報が自動的に使用されます。
     */
    @Bean
    public KmsClient kmsClient() {
        return KmsClient.create();
    }
}
