package com.freedom.taskall_v2.common.aws;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * AWS SDK for Java v2の{@link SsmClient}を用いて、SSM Parameter Store上の
 * SecureStringパラメータを取得する実装クラスです(issue #41)。
 *
 * <p>
 * {@code taskall.credential-init.enabled=true}の環境(本番のEC2インスタンス)でのみ
 * Bean登録される({@link com.freedom.taskall_v2.common.config.SsmClientConfig}参照)。
 * EC2のIAMインスタンスプロファイルにより、AWS SDKのデフォルト認証情報プロバイダチェーン経由で
 * 自動的に認証情報が解決されるため、本クラスではアクセスキー等を一切保持しない。
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "taskall.credential-init", name = "enabled", havingValue = "true")
public class AwsSsmParameterFetcher implements SsmParameterFetcher {

    private final SsmClient ssmClient;

    public AwsSsmParameterFetcher(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    @Override
    public Optional<String> fetchSecureString(String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            return Optional.ofNullable(ssmClient.getParameter(request).parameter().value());
        } catch (ParameterNotFoundException e) {
            // パラメータ未設定は呼び出し側で「起動不能な設定不備」として扱うため、ここでは空を返すのみとする
            return Optional.empty();
        }
    }
}
