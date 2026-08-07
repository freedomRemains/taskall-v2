package com.freedom.taskall_v2.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AWS SDK for Javaの{@link SsmClient}をBean登録する設定クラスです(issue #41)。
 *
 * <p>
 * {@code taskall.credential-init.enabled=true}の場合のみBean登録することで、
 * ローカル開発・単体テスト実行時にAWSリージョン/認証情報が未設定でもアプリ起動やテストの
 * コンテキスト読み込みに支障が出ないようにする({@code SsmClient.create()}はリージョン・
 * 認証情報の解決をクライアント生成時に行うため、無条件にBean化すると環境によっては
 * 起動時エラーになり得るため)。
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "taskall.credential-init", name = "enabled", havingValue = "true")
public class SsmClientConfig {

    /**
     * デフォルトのリージョン・認証情報プロバイダチェーンを使用する{@link SsmClient}を生成します。
     * 本番のEC2インスタンスでは、IAMインスタンスプロファイルの認証情報が自動的に使用されます。
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.create();
    }
}
