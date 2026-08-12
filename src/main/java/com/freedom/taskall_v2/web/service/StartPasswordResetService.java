package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;
import com.freedom.taskall_v2.web.util.PasswordStrengthValidator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * パスワード再設定の1画面目（メールアドレス・新パスワード入力）を処理するサービスです。
 */
@Service
public class StartPasswordResetService implements ScriptElementService {

    private static final String GUEST_ACCOUNT_ID = "1000001";
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";
    private static final String PASSWORD_CONFIRM_ERROR_GNR_KEY_VAL_ID = "1000406";
    private static final String PASSWORD_STRENGTH_ERROR_GNR_KEY_VAL_ID = "1000407";

    private final PasswordResetService passwordResetService;
    private final PasswordStrengthValidator passwordStrengthValidator;
    private final PasswordEncoder passwordEncoder;
    private final PasscodeGenerator passcodeGenerator;
    private final PasswordResetMailService passwordResetMailService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public StartPasswordResetService(PasswordResetService passwordResetService,
            PasswordStrengthValidator passwordStrengthValidator, PasswordEncoder passwordEncoder,
            PasscodeGenerator passcodeGenerator, PasswordResetMailService passwordResetMailService,
            ErrMsgService errMsgService, ObjectMapper objectMapper, MsgUtil msg) {
        this.passwordResetService = passwordResetService;
        this.passwordStrengthValidator = passwordStrengthValidator;
        this.passwordEncoder = passwordEncoder;
        this.passcodeGenerator = passcodeGenerator;
        this.passwordResetMailService = passwordResetMailService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String mailAddress = require(context, "MAIL_ADDRESS");
        String afterPassword = require(context, "AFTER_PASSWORD");
        String afterPasswordConfirm = require(context, "AFTER_PASSWORD_CONFIRM");

        // 入力チェックに失敗した場合は、同一画面へエラーメッセージ付きで戻す
        if (!afterPassword.equals(afterPasswordConfirm)) {
            return writeAsString(buildErrRedirect(sessionId, PASSWORD_CONFIRM_ERROR_GNR_KEY_VAL_ID));
        }
        if (!passwordStrengthValidator.isValid(afterPassword)) {
            return writeAsString(buildErrRedirect(sessionId, PASSWORD_STRENGTH_ERROR_GNR_KEY_VAL_ID));
        }

        // 同一メールアドレスの既存行があれば、ロック中(かつ期限内)のみエラー、それ以外は削除して再受付する
        List<LinkedHashMap<String, String>> existingRows = passwordResetService.findByMailAddress(mailAddress);
        boolean hasActiveLock = existingRows.stream()
                .anyMatch(row -> passwordResetService.isLocked(row) && !passwordResetService.isExpired(row));
        if (hasActiveLock) {
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID));
        }
        if (!existingRows.isEmpty()) {
            passwordResetService.deleteByMailAddress(mailAddress);
        }

        // 新しい再設定行を作成し、6桁コードをメール送信したら次画面へ遷移させる
        String passcode = passcodeGenerator.generate();
        String passwordResetId = passwordResetService.create(sessionId, mailAddress, passwordEncoder.encode(afterPassword),
                passwordEncoder.encode(passcode));
        try {
            passwordResetMailService.sendPasscode(mailAddress, passcode);
        } catch (ApplicationInternalException e) {
            passwordResetService.deleteById(passwordResetId);
            throw e;
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/resetPasscode.html");
        output.put("PENDING_PASSWORD_RESET_ID", passwordResetId);
        return writeAsString(output);
    }

    private String require(ObjectNode context, String fieldName) {
        String value = context.path(fieldName).asString("");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", fieldName));
        }
        return value;
    }

    private ObjectNode buildErrRedirect(String sessionId, String gnrKeyValId) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, GUEST_ACCOUNT_ID, gnrKeyValId);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/inputMail.html?errMsgKey=" + errMsgKey);
        return output;
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
