package com.freedom.taskall_v2.common.service.mail;

import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.MailAddrVerificationProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * jakarta.mail(実体はAngus Mail)を用いて、実際にIMAPでメールボックスへ接続することで
 * メールが読める状態かどうかを検証する実装クラスです(issue #96)。
 *
 * <p>
 * 監視対象メールアドレスは「Gmail」もしくは「平文認証の自社メールサーバ」のいずれかであることを
 * 前提としており、ドメインが{@code gmail.com}/{@code googlemail.com}の場合はGmail向けの
 * IMAPS接続(imap.gmail.com:993、SSL)を、それ以外は{@link MailAddrVerificationProperties}で
 * 設定した自社メールサーバ(平文認証)への接続を試みる。
 * </p>
 */
@Service
public class JavaMailMailboxAccessVerifier implements MailboxAccessVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JavaMailMailboxAccessVerifier.class);

    private static final String GMAIL_HOST = "imap.gmail.com";
    private static final int GMAIL_PORT = 993;

    private final MailAddrVerificationProperties mailAddrVerificationProperties;

    public JavaMailMailboxAccessVerifier(MailAddrVerificationProperties mailAddrVerificationProperties) {
        this.mailAddrVerificationProperties = mailAddrVerificationProperties;
    }

    @Override
    public boolean canAccess(String mailAddress, String password) {

        // ドメインに応じてGmail(IMAPS)/自社メールサーバ(平文IMAP)いずれの接続先を使うかを決定する
        boolean gmail = isGmailDomain(mailAddress);
        String protocol = gmail ? "imaps" : "imap";
        String host = gmail ? GMAIL_HOST : mailAddrVerificationProperties.getPlainHost();
        int port = gmail ? GMAIL_PORT : mailAddrVerificationProperties.getPlainPort();

        Session session = Session.getInstance(new Properties());
        try (Store store = session.getStore(protocol)) {
            store.connect(host, port, mailAddress, password);
            return true;
        } catch (MessagingException e) {
            // 認証失敗・接続不可など、メールが読めない事象は全て業務的な「読めなかった」として扱う
            logger.info("監視対象メールアドレスへの接続に失敗しました。mailAddress={}, host={}, port={}", mailAddress, host, port, e);
            return false;
        }
    }

    /**
     * メールアドレスのドメイン部が、Gmail(gmail.com/googlemail.com)かどうかを判定します。
     */
    boolean isGmailDomain(String mailAddress) {
        int atIndex = mailAddress.lastIndexOf('@');
        if (atIndex < 0) {
            return false;
        }
        String domain = mailAddress.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return "gmail.com".equals(domain) || "googlemail.com".equals(domain);
    }
}
