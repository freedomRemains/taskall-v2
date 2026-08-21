package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 監視対象メールアドレス(issue #96)の接続確認に使用する、自社メールサーバ(平文認証)向けの設定を保持するクラスです。
 *
 * <p>
 * 「taskall.mail-addr-verification」配下の値をバインドします。監視対象メールアドレスは
 * 「Gmail」もしくは「平文認証の自社メールサーバ」のいずれかであることを前提としており(issue #96)、
 * Gmail向けの接続先(imap.gmail.com:993、IMAPS)は固定値のため設定不要ですが、自社メールサーバ側の
 * 接続先はローカル・本番で異なるため、本設定クラスで注入可能にしています。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "taskall.mail-addr-verification")
public class MailAddrVerificationProperties {

    /** 自社メールサーバ(平文認証)のホスト名 */
    private String plainHost = "localhost";

    /** 自社メールサーバ(平文認証)のポート番号 */
    private int plainPort = 143;

    public String getPlainHost() {
        return plainHost;
    }

    public void setPlainHost(String plainHost) {
        this.plainHost = plainHost;
    }

    public int getPlainPort() {
        return plainPort;
    }

    public void setPlainPort(int plainPort) {
        this.plainPort = plainPort;
    }
}
