package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.RecaptchaProperties;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RecaptchaVerificationServiceTest {

    @Mock
    private MsgUtil msgUtil;

    private RecaptchaProperties properties;
    private TestRecaptchaVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new RecaptchaProperties();
        service = new TestRecaptchaVerificationService(properties, new ObjectMapper(), msgUtil);
    }

    @Test
    void reCAPTCHA応答が空の場合はAPI呼び出しせずfalseを返すこと() {

        properties.setSecretKey("secret");

        assertThat(service.verify(" ")).isFalse();
        assertThat(service.called).isFalse();
    }

    @Test
    void secretKeyが未設定の場合はフェイルオープンでtrueを返すこと() {

        properties.setSecretKey("");

        assertThat(service.verify("token")).isTrue();
        assertThat(service.called).isFalse();
    }

    @Test
    void Google応答のsuccessがtrueの場合はtrueを返すこと() {

        properties.setSecretKey("secret");
        service.response = Map.of("success", true);

        assertThat(service.verify("token")).isTrue();
        assertThat(service.called).isTrue();
        assertThat(service.secret).isEqualTo("secret");
        assertThat(service.recaptchaResponse).isEqualTo("token");
    }

    @Test
    void API呼び出し失敗時はwarnログを出してfalseを返すこと() {

        properties.setSecretKey("secret");
        service.exception = new RuntimeException("boom");
        when(msgUtil.get("msg.warn.web.recaptchaVerificationApiError", "boom"))
                .thenReturn("recaptcha api failed: boom");

        assertThat(service.verify("token")).isFalse();
        verify(msgUtil).get("msg.warn.web.recaptchaVerificationApiError", "boom");
    }

    private static class TestRecaptchaVerificationService extends RecaptchaVerificationService {

        private boolean called;
        private String secret;
        private String recaptchaResponse;
        private Map<String, Object> response;
        private RuntimeException exception;

        TestRecaptchaVerificationService(RecaptchaProperties properties, ObjectMapper objectMapper, MsgUtil msgUtil) {
            super(properties, objectMapper, msgUtil);
        }

        @Override
        protected Map<String, Object> callSiteVerifyApi(String secretKey, String recaptchaResponse) {
            called = true;
            secret = secretKey;
            this.recaptchaResponse = recaptchaResponse;
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
