package com.freedom.taskall_v2.web.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * テスト用のメール送信サービス設定クラスです。
 *
 * <p>
 * {@link TwoFactorMailService}を何もしないスタブ実装で置き換え、テスト実行時に
 * 実際のメールサーバへの接続を回避します。
 * </p>
 */
@TestConfiguration
public class TwoFactorMailServiceTestConfig {

    @Bean
    @Primary
    public TwoFactorMailService twoFactorMailService() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MsgUtil mockMsgUtil = mock(MsgUtil.class);
        TwoFactorMailService mockService = mock(TwoFactorMailService.class);
        
        // sendPasscodeメソッドが呼ばれても何もしない
        doNothing().when(mockService).sendPasscode(anyString(), anyString());
        
        return mockService;
    }
}
