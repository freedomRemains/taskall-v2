package com.freedom.taskall_v2.common.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.MailAddrVerificationProperties;

@ExtendWith(MockitoExtension.class)
class JavaMailMailboxAccessVerifierTest {

    @Mock
    private MailAddrVerificationProperties mailAddrVerificationProperties;

    @InjectMocks
    private JavaMailMailboxAccessVerifier javaMailMailboxAccessVerifier;

    @Test
    void gmail_comドメインはGmailと判定すること() {
        assertThat(javaMailMailboxAccessVerifier.isGmailDomain("user@gmail.com")).isTrue();
    }

    @Test
    void googlemail_comドメインもGmailと判定すること() {
        assertThat(javaMailMailboxAccessVerifier.isGmailDomain("USER@GoogleMail.COM")).isTrue();
    }

    @Test
    void 自社メールサーバのドメインはGmailと判定しないこと() {
        assertThat(javaMailMailboxAccessVerifier.isGmailDomain("user@example.co.jp")).isFalse();
    }

    @Test
    void アットマークが無い場合はGmailと判定しないこと() {
        assertThat(javaMailMailboxAccessVerifier.isGmailDomain("invalid-mail-address")).isFalse();
    }

    @Test
    void 接続できないホストへのアクセスは例外を投げずfalseを返すこと() {

        // 平文認証(自社メールサーバ)側の接続先を、何も待ち受けていないローカルポートに向けることで、
        // ネットワークに依存せず即座に接続失敗(MessagingException)を再現する
        when(mailAddrVerificationProperties.getPlainHost()).thenReturn("localhost");
        when(mailAddrVerificationProperties.getPlainPort()).thenReturn(1);

        boolean result = javaMailMailboxAccessVerifier.canAccess("user@example.co.jp", "dummyPassword");

        assertThat(result).isFalse();
    }

    @Test
    void 実際のIMAPサーバへの正しい認証情報でのログインはtrueを返すこと() throws Exception {

        // MockitoでMailboxAccessVerifierの内部をモック化するだけでは、実際のIMAP
        // ワイヤプロトコル(CAPABILITY/LOGIN)を通した検証ができていなかった(issue #96の
        // ユーザ報告により判明)。FakeImapServerで最小限のIMAP応答を返す実サーバを立て、
        // 本物のjakarta.mail(Angus Mail)実装を通して接続確認する
        try (FakeImapServer fakeImapServer = new FakeImapServer("user@example.co.jp", "correctPassword")) {
            when(mailAddrVerificationProperties.getPlainHost()).thenReturn("localhost");
            when(mailAddrVerificationProperties.getPlainPort()).thenReturn(fakeImapServer.getPort());

            boolean result = javaMailMailboxAccessVerifier.canAccess("user@example.co.jp", "correctPassword");

            assertThat(result).isTrue();
        }
    }

    @Test
    void 実際のIMAPサーバへの誤った認証情報でのログインはfalseを返すこと() throws Exception {

        try (FakeImapServer fakeImapServer = new FakeImapServer("user@example.co.jp", "correctPassword")) {
            when(mailAddrVerificationProperties.getPlainHost()).thenReturn("localhost");
            when(mailAddrVerificationProperties.getPlainPort()).thenReturn(fakeImapServer.getPort());

            boolean result = javaMailMailboxAccessVerifier.canAccess("user@example.co.jp", "wrongPassword");

            assertThat(result).isFalse();
        }
    }
}
