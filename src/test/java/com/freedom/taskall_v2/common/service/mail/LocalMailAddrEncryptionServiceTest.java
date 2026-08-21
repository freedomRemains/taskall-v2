package com.freedom.taskall_v2.common.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalMailAddrEncryptionServiceTest {

    private final LocalMailAddrEncryptionService localMailAddrEncryptionService = new LocalMailAddrEncryptionService();

    @Test
    void 暗号化した文字列を復号すると元の平文に戻ること() {

        String plainText = "P@ssw0rd!";

        String encrypted = localMailAddrEncryptionService.encrypt(plainText);
        String decrypted = localMailAddrEncryptionService.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(decrypted).isEqualTo(plainText);
    }
}
