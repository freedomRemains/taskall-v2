package com.freedom.taskall_v2.common.service.mail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.MailAddrEncryptionProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * AWS KMSを用いて監視対象メールアドレス(issue #96)のパスワードを暗号化・復号する実装クラスです。
 *
 * <p>
 * {@code taskall.mail-addr-encryption.enabled=true}の環境でのみBean登録される
 * ({@link KmsClientConfig}参照)。KMSの{@code Encrypt}/{@code Decrypt} APIをそのまま利用し、
 * 返却された暗号文バイト列をBase64文字列化してDBへ保存できる形にする(エンベロープ暗号化)。
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "taskall.mail-addr-encryption", name = "enabled", havingValue = "true")
public class KmsMailAddrEncryptionService implements MailAddrEncryptionService {

    private final KmsClient kmsClient;
    private final MailAddrEncryptionProperties mailAddrEncryptionProperties;
    private final MsgUtil msg;

    public KmsMailAddrEncryptionService(KmsClient kmsClient, MailAddrEncryptionProperties mailAddrEncryptionProperties,
            MsgUtil msg) {
        this.kmsClient = kmsClient;
        this.mailAddrEncryptionProperties = mailAddrEncryptionProperties;
        this.msg = msg;
    }

    @Override
    public String encrypt(String plainText) {
        try {
            EncryptRequest request = EncryptRequest.builder()
                    .keyId(mailAddrEncryptionProperties.getKmsKeyId())
                    .plaintext(SdkBytes.fromUtf8String(plainText))
                    .build();
            SdkBytes ciphertext = kmsClient.encrypt(request).ciphertextBlob();
            return Base64.getEncoder().encodeToString(ciphertext.asByteArray());
        } catch (KmsException e) {
            // KMSとの通信・キー不備等は業務ルールと無関係な技術的異常のため、システムエラーとして扱う
            throw new ApplicationInternalException(msg.get("msg.err.web.mailAddrRegister.encryptionFailed"), e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        try {
            SdkBytes ciphertext = SdkBytes.fromByteArray(Base64.getDecoder().decode(encryptedText));
            DecryptRequest request = DecryptRequest.builder()
                    .keyId(mailAddrEncryptionProperties.getKmsKeyId())
                    .ciphertextBlob(ciphertext)
                    .build();
            SdkBytes plaintext = kmsClient.decrypt(request).plaintext();
            return new String(plaintext.asByteArray(), StandardCharsets.UTF_8);
        } catch (KmsException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.mailAddrRegister.decryptionFailed"), e);
        }
    }
}
