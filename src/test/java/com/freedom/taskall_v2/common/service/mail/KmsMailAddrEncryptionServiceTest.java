package com.freedom.taskall_v2.common.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.MailAddrEncryptionProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

@ExtendWith(MockitoExtension.class)
class KmsMailAddrEncryptionServiceTest {

    @Mock
    private KmsClient kmsClient;

    @Mock
    private MailAddrEncryptionProperties mailAddrEncryptionProperties;

    @Mock
    private MsgUtil msg;

    @Test
    void 暗号化はKMSの暗号文をBase64化して返却すること() {

        when(mailAddrEncryptionProperties.getKmsKeyId()).thenReturn("alias/mail-addr");
        SdkBytes ciphertext = SdkBytes.fromUtf8String("cipher-bytes");
        when(kmsClient.encrypt(any(EncryptRequest.class)))
                .thenReturn(EncryptResponse.builder().ciphertextBlob(ciphertext).build());

        KmsMailAddrEncryptionService service =
                new KmsMailAddrEncryptionService(kmsClient, mailAddrEncryptionProperties, msg);

        String result = service.encrypt("plainPassword");

        assertThat(result).isEqualTo(Base64.getEncoder().encodeToString(ciphertext.asByteArray()));
    }

    @Test
    void 復号はBase64をKMSへ渡し平文を返却すること() {

        when(mailAddrEncryptionProperties.getKmsKeyId()).thenReturn("alias/mail-addr");
        SdkBytes plaintext = SdkBytes.fromUtf8String("plainPassword");
        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenReturn(DecryptResponse.builder().plaintext(plaintext).build());

        KmsMailAddrEncryptionService service =
                new KmsMailAddrEncryptionService(kmsClient, mailAddrEncryptionProperties, msg);

        String encoded = Base64.getEncoder().encodeToString("cipher-bytes".getBytes(StandardCharsets.UTF_8));
        String result = service.decrypt(encoded);

        assertThat(result).isEqualTo("plainPassword");
    }

    @Test
    void 暗号化失敗時はApplicationInternalExceptionとなること() {

        when(mailAddrEncryptionProperties.getKmsKeyId()).thenReturn("alias/mail-addr");
        when(kmsClient.encrypt(any(EncryptRequest.class)))
                .thenThrow(KmsException.builder().message("kms error").build());
        when(msg.get("msg.err.web.mailAddrRegister.encryptionFailed")).thenReturn("encryption failed");

        KmsMailAddrEncryptionService service =
                new KmsMailAddrEncryptionService(kmsClient, mailAddrEncryptionProperties, msg);

        org.junit.jupiter.api.Assertions.assertThrows(ApplicationInternalException.class,
                () -> service.encrypt("plainPassword"));
    }
}
