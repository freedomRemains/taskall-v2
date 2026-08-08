package com.freedom.taskall_v2.web.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.MailProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 二段階認証用の6桁パスコードをメールで送信するサービスです。
 *
 * <p>
 * タイトルは「二段階認証パスコード」、本文は「次の6桁の数字をご入力ください。」の次の行に
 * 半角スペース2文字＋3桁＋半角スペース＋3桁の書式(例: {@code "  042 817"})でパスコードを
 * 記載する(設計書「二段階認証の処理概略」節)。
 * </p>
 */
@Service
public class TwoFactorMailService {

    private static final String MAIL_SUBJECT = "二段階認証パスコード";

    private final JavaMailSender javaMailSender;
    private final MsgUtil msg;
    private final MailProperties mailProperties;

    public TwoFactorMailService(JavaMailSender javaMailSender, MsgUtil msg, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.msg = msg;
        this.mailProperties = mailProperties;
    }

    /**
     * 指定したメールアドレスへ、6桁のパスコードを記載したメールを送信します。
     *
     * @param mailAddress 送信先メールアドレス
     * @param passcode    6桁のパスコード文字列(例: {@code "042817"})
     * @throws ApplicationInternalException メール送信に失敗した場合
     */
    public void sendPasscode(String mailAddress, String passcode) {

        String formattedPasscode = passcode.substring(0, 3) + " " + passcode.substring(3);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromAddress());
        message.setTo(mailAddress);
        message.setSubject(MAIL_SUBJECT);
        message.setText("次の6桁の数字をご入力ください。\n  " + formattedPasscode);

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.twoFactor.mailSendFailed", mailAddress), e);
        }
    }
}
