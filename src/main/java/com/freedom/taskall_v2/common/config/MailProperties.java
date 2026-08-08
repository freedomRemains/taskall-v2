package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * アプリが送信するメール(二段階認証パスコード等)に共通で使う、アプリ独自の設定を保持するクラス。
 *
 * <p>
 * SpringBoot自体のSMTP接続設定(application-[環境名].yml、spring.mail.*)ではなく、
 * アプリ独自の設定(custom-[環境名].yml)の「taskall.mail」配下の値をバインドする。
 * </p>
 *
 * <p>
 * 送信元(From)アドレスを明示的に設定しない場合、{@code JavaMailSender}はOS/JVMの
 * デフォルト値(実行ユーザー名@ホスト名)を自動生成してしまい、本番のAWS SES環境では
 * 未検証の送信元として拒否される(issue #66)。SESの検証はドメイン単位で行っており、
 * ドメイン配下の任意のローカルパートでの送信が許可されるため、実在するメールボックスを
 * 別途用意する必要はない。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "taskall.mail")
public class MailProperties {

    /** メール送信時の送信元(From)アドレス */
    private String fromAddress = "no-reply@taskall-v2.com";

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
}
