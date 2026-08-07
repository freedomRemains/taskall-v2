package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * デフォルトアカウント(シードデータ)のパスワードを、AWS SSM Parameter Store経由で
 * 本番用の値へ差し替える機能(issue #41)のアプリ独自設定を保持するクラス。
 *
 * <p>
 * SpringBoot自体の設定(application-[環境名].yml)ではなく、アプリ独自の設定
 * (custom-[環境名].yml)の「taskall.credential-init」配下の値をバインドする。
 * {@code enabled=false}(デフォルト)の環境では、AWS SDKのクライアントBean自体を生成せず、
 * 起動時のパスワード差し替え処理も一切実行しない(ローカル開発・単体テスト実行時に
 * AWS認証情報が無くても支障が出ないようにするため)。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "taskall.credential-init")
public class CredentialInitProperties {

    /** SSM経由のパスワード差し替え処理を有効化するかどうか(デフォルトは無効) */
    private boolean enabled = false;

    /** SSMパラメータ名の接頭辞(この配下に「/{accountKey}/password」の形式で配置する) */
    private String parameterPrefix = "/taskall-v2/accnt";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getParameterPrefix() {
        return parameterPrefix;
    }

    public void setParameterPrefix(String parameterPrefix) {
        this.parameterPrefix = parameterPrefix;
    }
}
