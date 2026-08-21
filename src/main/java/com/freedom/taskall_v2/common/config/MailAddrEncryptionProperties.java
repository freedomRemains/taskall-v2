package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 監視対象メールアドレスのパスワード(issue #96)を、AWS KMSによるエンベロープ暗号化で
 * 保護するための設定を保持するクラスです。
 *
 * <p>
 * 「taskall.mail-addr-encryption」配下の値をバインドします。{@code enabled=false}(デフォルト)の
 * 環境では、KMSクライアントのBean自体を生成せず、ローカル開発・単体テスト実行時にAWS認証情報が
 * 無くても支障が出ないようにします({@link KmsClientConfig}参照)。この場合は
 * {@code LocalMailAddrEncryptionService}が代わりに使用されます。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "taskall.mail-addr-encryption")
public class MailAddrEncryptionProperties {

    /** KMSによる暗号化を有効化するかどうか(デフォルトは無効) */
    private boolean enabled = false;

    /** 暗号化に使用するKMSキーのID(キーARNもしくはエイリアス名) */
    private String kmsKeyId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKmsKeyId() {
        return kmsKeyId;
    }

    public void setKmsKeyId(String kmsKeyId) {
        this.kmsKeyId = kmsKeyId;
    }
}
