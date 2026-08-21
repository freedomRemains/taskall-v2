package com.freedom.taskall_v2.common.service.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ローカル開発・単体テスト環境向けの{@link MailAddrEncryptionService}実装クラスです(issue #96)。
 *
 * <p>
 * {@code taskall.mail-addr-encryption.enabled=false}(デフォルト)の環境で使用されます。
 * AWS KMSを使用しないため、単なるBase64エンコードによる可逆変換のみを行います。
 * <b>これは平文保存に近い簡易実装であり、本番相当のセキュリティは提供しません。</b>
 * 本番環境では必ず{@code taskall.mail-addr-encryption.enabled=true}を設定し、
 * {@link KmsMailAddrEncryptionService}を使用してください。
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "taskall.mail-addr-encryption", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LocalMailAddrEncryptionService implements MailAddrEncryptionService {

    @Override
    public String encrypt(String plainText) {
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String encryptedText) {
        return new String(Base64.getDecoder().decode(encryptedText), StandardCharsets.UTF_8);
    }
}
