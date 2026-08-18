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
 * サインアップの2画面目（6桁コード入力）を検証し、必要に応じてアカウントを作成するサービスです。
 */
@Service
public class VerifySignUpService implements ScriptElementService {

    private static final String GUEST_ACCOUNT_ID = "1000001";
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";
    private static final String SIGN_UP_CODE_ERROR_GNR_KEY_VAL_ID = "1000405";
    private static final String MAIL_EXISTS_ERROR_GNR_KEY_VAL_ID = "1000409";
    private static final String SIGN_UP_COMPLETE_NOTICE_GNR_KEY_VAL_ID = "1000106";

    private final SignUpService signUpService;
    private final PasswordEncoder passwordEncoder;
    private final ErrMsgService errMsgService;
    private final NoticeService noticeService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public VerifySignUpService(SignUpService signUpService, PasswordEncoder passwordEncoder,
            ErrMsgService errMsgService, NoticeService noticeService, ObjectMapper objectMapper, MsgUtil msg) {
        this.signUpService = signUpService;
        this.passwordEncoder = passwordEncoder;
        this.errMsgService = errMsgService;
        this.noticeService = noticeService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String inputCode = require(context, "SIGN_UP_CODE");
        String signUpId = context.path("pendingSignUpId").asString("");

        // セッション切れ等でサインアップIDを失っている場合も、有効期限切れと同様に黙ってTOPへ戻す
        if (signUpId.isBlank()) {
            return writeAsString(buildTopRedirect(true));
        }

        // セッションに保持されたサインアップIDで対象行を取得できない場合は、黙ってTOPへ戻す
        Optional<LinkedHashMap<String, String>> signUp = signUpService.findById(signUpId);
        if (signUp.isEmpty()) {
            return writeAsString(buildTopRedirect(true));
        }

        LinkedHashMap<String, String> row = signUp.get();
        if (!sessionId.equals(row.get("SESSION_ID"))) {
            return writeAsString(buildTopRedirect(true));
        }

        // ロック中は期限内ならエラー表示、期限切れなら行削除で受付終了とする
        if (signUpService.isLocked(row)) {
            if (signUpService.isExpired(row)) {
                signUpService.deleteById(signUpId);
                return writeAsString(buildTopRedirect(true));
            }
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }

        // 未ロック状態の有効期限切れは、その場でロックへ遷移させて15分待機とする
        if (signUpService.isExpired(row)) {
            signUpService.lock(signUpId);
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }

        // POST受付の間に同一メールアドレスのアカウントが作成された場合は、行を削除しエラー表示する
        String mailAddress = row.get("MAIL_ADDRESS");
        if (signUpService.findAccountByMailAddress(mailAddress).isPresent()) {
            signUpService.deleteById(signUpId);
            return writeAsString(buildErrRedirect(sessionId, MAIL_EXISTS_ERROR_GNR_KEY_VAL_ID, false));
        }

        // 6桁コードが一致すればアカウントを作成し、サインアップ行を削除してTOPへ遷移させる
        if (passwordEncoder.matches(inputCode, row.get("PASSCODE_HASH"))) {
            signUpService.createAccount(row.get("ACCOUNT_NAME"), mailAddress, row.get("PASSWORD_HASH"),
                    row.get("APROLE_ID"));
            signUpService.deleteById(signUpId);
            return writeAsString(buildTopRedirectWithCompleteNotice(sessionId));
        }

        // 6桁コード不一致は失敗回数を1回加算し、5回到達でロック、未到達なら再入力を促す
        boolean locked = signUpService.recordFailureAndLockIfNeeded(signUpId, Integer.parseInt(row.get("FAIL_CNT")));
        if (locked) {
            return writeAsString(buildErrRedirect(sessionId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID, false));
        }
        return writeAsString(buildErrRedirect(sessionId, SIGN_UP_CODE_ERROR_GNR_KEY_VAL_ID, false));
    }

    private String require(ObjectNode context, String fieldName) {
        String value = context.path(fieldName).asString("");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", fieldName));
        }
        return value;
    }

    private ObjectNode buildTopRedirect(boolean signUpCompleted) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/top.html");
        output.put("signUpCompleted", signUpCompleted);
        return output;
    }

    // サインアップ完了時のみ、NTC(通知)へ完了メッセージを登録し、TOP画面遷移後に表示させる
    private ObjectNode buildTopRedirectWithCompleteNotice(String sessionId) {
        String noticeKey = noticeService.getNoticeKey(sessionId, GUEST_ACCOUNT_ID, SIGN_UP_COMPLETE_NOTICE_GNR_KEY_VAL_ID);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/top.html?noticeKey=" + noticeKey);
        output.put("signUpCompleted", true);
        return output;
    }

    private ObjectNode buildErrRedirect(String sessionId, String gnrKeyValId, boolean signUpCompleted) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, GUEST_ACCOUNT_ID, gnrKeyValId);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/signUpPasscode.html?errMsgKey=" + errMsgKey);
        output.put("signUpCompleted", signUpCompleted);
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
