package com.freedom.taskall_v2.web.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.freedom.taskall_v2.common.config.RecaptchaProperties;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;

/**
 * Google reCAPTCHA siteverify APIを呼び出し、チェックボックス認証結果を検証するサービスです。
 */
@Service
public class RecaptchaVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(RecaptchaVerificationService.class);
    private static final String SITE_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RecaptchaProperties recaptchaProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public RecaptchaVerificationService() {
        this(new RecaptchaProperties(), new ObjectMapper(), new MsgUtil());
    }

    public RecaptchaVerificationService(RecaptchaProperties recaptchaProperties, ObjectMapper objectMapper,
            MsgUtil msg) {
        this(recaptchaProperties, RestClient.create(), objectMapper, msg);
    }

    RecaptchaVerificationService(RecaptchaProperties recaptchaProperties, RestClient restClient,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.recaptchaProperties = recaptchaProperties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    public boolean verify(String recaptchaResponse) {

        if (recaptchaResponse == null || recaptchaResponse.isBlank()) {
            return false;
        }

        String secretKey = recaptchaProperties.getSecretKey();

        // ローカル/CIでキー未設定でも既存機能全体を止めないため、未設定時は警告の上でフェイルオープンとする。
        // 本番ではcustom-prod.yaml経由で環境変数から必ず注入される前提。
        if (secretKey == null || secretKey.isBlank()) {
            logger.warn(msg.get("msg.warn.web.recaptchaSecretKeyMissing"));
            return true;
        }

        try {
            Map<String, Object> responseBody = callSiteVerifyApi(secretKey, recaptchaResponse);
            return Boolean.TRUE.equals(responseBody.get("success"));
        } catch (RuntimeException e) {
            logger.warn(msg.get("msg.warn.web.recaptchaVerificationApiError", e.getMessage()));
            return false;
        }
    }

    protected Map<String, Object> callSiteVerifyApi(String secretKey, String recaptchaResponse) {
        String requestBody = "secret=" + urlEncode(secretKey) + "&response=" + urlEncode(recaptchaResponse);
        String responseBody = restClient.post()
                .uri(SITE_VERIFY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        return objectMapper.convertValue(readJson(responseBody), Map.class);
    }

    private Object readJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
