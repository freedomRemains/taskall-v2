package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google reCAPTCHA v2のサイトキー・シークレットキーを保持するクラスです。
 *
 * <p>
 * SpringBoot自体の設定ではなく、アプリ独自設定(custom-[環境名].yml)の
 * {@code taskall.recaptcha}配下の値をバインドします。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "taskall.recaptcha")
public class RecaptchaProperties {

    /** フロント側ウィジェット描画用のサイトキー */
    private String siteKey = "";

    /** Google siteverify API呼び出し用のシークレットキー */
    private String secretKey = "";

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
