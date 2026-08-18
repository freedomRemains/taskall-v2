package com.freedom.taskall_v2.web.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.MailProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * サインアップ確認用の6桁コードをメール送信するサービスです。
 */
@Service
public class SignUpMailService {

    private static final String MAIL_SUBJECT = "サインアップ確認";

    private final JavaMailSender javaMailSender;
    private final MsgUtil msg;
    private final MailProperties mailProperties;

    public SignUpMailService(JavaMailSender javaMailSender, MsgUtil msg, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.msg = msg;
        this.mailProperties = mailProperties;
    }

    /**
     * 指定したメールアドレスへ、サインアップ確認用の6桁コードを送信します。
     */
    public void sendPasscode(String mailAddress, String passcode) {

        String formattedPasscode = passcode.substring(0, 3) + " " + passcode.substring(3);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromAddress());
        message.setTo(mailAddress);
        message.setSubject(MAIL_SUBJECT);
        message.setText("サインアップを完了するため、次の6桁の数字を確認画面にご入力ください。\n  " + formattedPasscode);

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.signUp.mailSendFailed", mailAddress), e);
        }
    }
}
