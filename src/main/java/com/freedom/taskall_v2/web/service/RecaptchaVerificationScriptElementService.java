package com.freedom.taskall_v2.web.service;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * サインアップ/パスワード再設定の入口でreCAPTCHA検証を行うスクリプト要素です。
 */
@Service
public class RecaptchaVerificationScriptElementService implements ScriptElementService {

    private static final String GUEST_ACCOUNT_ID = "1000001";
    private static final String RECAPTCHA_ERROR_GNR_KEY_VAL_ID = "1000410";

    private final RecaptchaVerificationService recaptchaVerificationService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public RecaptchaVerificationScriptElementService(RecaptchaVerificationService recaptchaVerificationService,
            ErrMsgService errMsgService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recaptchaVerificationService = recaptchaVerificationService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String requestUri = require(context, "requestUri");
        String recaptchaResponse = require(context, "g-recaptcha-response");

        if (recaptchaVerificationService.verify(recaptchaResponse)) {
            return contextJson;
        }

        String errMsgKey = errMsgService.getErrMsgKey(sessionId, GUEST_ACCOUNT_ID, RECAPTCHA_ERROR_GNR_KEY_VAL_ID);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", requestUri + "?errMsgKey=" + errMsgKey);
        return writeAsString(output);
    }

    private String require(ObjectNode context, String fieldName) {
        String value = context.path(fieldName).asString("");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", fieldName));
        }
        return value;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
