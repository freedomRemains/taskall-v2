package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.freedom.taskall_v2.common.config.MailProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class SignUpMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private SignUpMailService signUpMailService;

    @BeforeEach
    void setUp() {
        signUpMailService = new SignUpMailService(javaMailSender, new MsgUtil(), new MailProperties());
    }

    @Test
    void 宛先件名本文送信元を指定してメールが送信されること() {

        signUpMailService.sendPasscode("user@example.com", "042817");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo("no-reply@taskall-v2.com");
        assertThat(sentMessage.getTo()).containsExactly("user@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("サインアップ確認");
        assertThat(sentMessage.getText()).isEqualTo("サインアップを完了するため、次の6桁の数字を確認画面にご入力ください。\n  042 817");
    }

    @Test
    void 送信に失敗した場合はApplicationInternalExceptionがスローされること() {

        doThrow(new MailSendException("smtp error")).when(javaMailSender)
                .send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> signUpMailService.sendPasscode("user@example.com", "042817"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
