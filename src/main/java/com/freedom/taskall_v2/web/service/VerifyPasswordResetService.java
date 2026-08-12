package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * パスワード再設定の2画面目（6桁コード入力）を検証し、必要に応じてパスワードを更新するサービスです。
 */
@Service
public class VerifyPasswordResetService implements ScriptElementService {

    private static final String GUEST_ACCOUNT_ID = "1000001";
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";
    private static final String PASSWORD_RESET_CODE_ERROR_GNR_KEY_VAL_ID = "1000405";

    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public VerifyPasswordResetService(PasswordResetService passwordResetService, PasswordEncoder passwordEncoder,
            ErrMsgService errMsgService, ObjectMapper objectMapper, MsgUtil msg) {
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String inputCode = require(context, "PASSWORD_RESET_CODE");
        String passwordResetId = context.path("pendingPasswordResetId").asString("");

        // セッション切れ等で再設定IDを失っている場合も、有効期限切れと同様に黙ってTOPへ戻す
        if (passwordResetId.isBlank()) {
            return writeAsString(buildTopRedirect(true));
        }

        // セッションに保持された再設定IDで対象行を取得できない場合は、黙ってTOPへ戻す
        Optional<LinkedHashMap<String, String>> passwordReset = passwordResetService.findById(passwordResetId);
        if (passwordReset.isEmpty()) {
            return writeAsString(buildTopRedirect(true));
        }

        LinkedHashMap<String, String> row = passwordReset.get();
        if (!sessionId.equals(row.get("SESSION_ID"))) {
            return writeAsString(buildTopRedirect(true));
        }

        // ロック中は期限内ならエラー表示、期限切れなら行削除で受付終了とする
        if (passwordResetService.isLocked(row)) {
            if (passwordResetService.isExpired(row)) {
                passwordResetService.deleteById(passwordResetId);
                return writeAsString(buildTopRedirect(true));
            }
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }

        // 未ロック状態の有効期限切れは、その場でロックへ遷移させて15分待機とする
        if (passwordResetService.isExpired(row)) {
            passwordResetService.lock(passwordResetId);
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }

        // メールアドレスの存在有無と6桁一致判定は、応答差を抑えるため両方とも毎回評価する
        Optional<LinkedHashMap<String, String>> account = passwordResetService.findAccountByMailAddress(row.get("MAIL_ADDRESS"));
        boolean passcodeMatches = passwordEncoder.matches(inputCode, row.get("PASSCODE_HASH"));
        boolean canResetPassword = account.isPresent() && passcodeMatches;

        if (canResetPassword) {
            passwordResetService.updateAccountPassword(account.get().get("ACCNT_ID"), row.get("AFTER_PASSWORD_HASH"));
            passwordResetService.deleteById(passwordResetId);
            return writeAsString(buildTopRedirect(true));
        }

        boolean locked = passwordResetService.recordFailureAndLockIfNeeded(passwordResetId,
                Integer.parseInt(row.get("FAIL_CNT")));
        if (locked) {
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }
        return writeAsString(buildErrRedirect(sessionId, PASSWORD_RESET_CODE_ERROR_GNR_KEY_VAL_ID, false));
    }

    private String require(ObjectNode context, String fieldName) {
        String value = context.path(fieldName).asString("");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", fieldName));
        }
        return value;
    }

    private ObjectNode buildTopRedirect(boolean passwordResetCompleted) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/top.html");
        output.put("passwordResetCompleted", passwordResetCompleted);
        return output;
    }

    private ObjectNode buildErrRedirect(String sessionId, String gnrKeyValId, boolean passwordResetCompleted) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, GUEST_ACCOUNT_ID, gnrKeyValId);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/resetPasscode.html?errMsgKey=" + errMsgKey);
        output.put("passwordResetCompleted", passwordResetCompleted);
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
