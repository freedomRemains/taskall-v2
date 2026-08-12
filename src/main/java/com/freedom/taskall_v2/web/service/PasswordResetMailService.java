package com.freedom.taskall_v2.web.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.MailProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * パスワード再設定用の6桁コードをメール送信するサービスです。
 */
@Service
public class PasswordResetMailService {

    private static final String MAIL_SUBJECT = "パスワード再設定";

    private final JavaMailSender javaMailSender;
    private final MsgUtil msg;
    private final MailProperties mailProperties;

    public PasswordResetMailService(JavaMailSender javaMailSender, MsgUtil msg, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.msg = msg;
        this.mailProperties = mailProperties;
    }

    /**
     * 指定したメールアドレスへ、パスワード再設定用の6桁コードを送信します。
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
            throw new ApplicationInternalException(msg.get("msg.err.web.passwordReset.mailSendFailed", mailAddress), e);
        }
    }
}
