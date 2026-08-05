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
 * 二段階認証画面から入力された6桁のパスコードを照合するサービスです。
 *
 * <p>
 * 設計書(documents/design/2000006_two_phase_login.md)「二段階認証処理」節の二次認証手順を
 * そのまま実装します。成功時は{@code account}配列を出力し、既存の
 * {@code TaskallV2Controller#storeAccountIdIfExists}の仕組みでセッションへ
 * {@code accountId}を格納することで、実際のログインを確立します。
 * </p>
 */
@Service
public class VerifyTwoFactorAuthService implements ScriptElementService {

    /** アカウントロック中エラーメッセージに対応する汎用キー値マスタID */
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";

    /** 二段階認証有効期限切れエラーメッセージに対応する汎用キー値マスタID */
    private static final String TWO_FACTOR_EXPIRED_ERROR_GNR_KEY_VAL_ID = "1000403";

    /** 二段階認証コード不一致エラーメッセージに対応する汎用キー値マスタID */
    private static final String TWO_FACTOR_CODE_ERROR_GNR_KEY_VAL_ID = "1000404";

    private final LoginStatusService loginStatusService;
    private final AccntAuthLockService accntAuthLockService;
    private final PasswordEncoder passwordEncoder;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public VerifyTwoFactorAuthService(LoginStatusService loginStatusService,
            AccntAuthLockService accntAuthLockService, PasswordEncoder passwordEncoder, ErrMsgService errMsgService,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.loginStatusService = loginStatusService;
        this.accntAuthLockService = accntAuthLockService;
        this.passwordEncoder = passwordEncoder;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = context.path("sessionId").asString("");
        String accountId = context.path("pendingTwoFactorAccountId").asString("");
        if (accountId.isBlank()) {
            throw new BusinessRuleViolationException(
                    msg.get("msg.warn.web.requiredParamMissing", "pendingTwoFactorAccountId"));
        }
        String inputCode = context.path("TWO_FACTOR_CODE").asString("");
        if (inputCode.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", "TWO_FACTOR_CODE"));
        }

        // アカウント全体がロック中の場合は、他セッションの失敗が原因の場合も含め一律で処理を打ち切る
        if (accntAuthLockService.isLocked(accountId)) {
            return writeAsString(buildLockedResponse(sessionId, accountId));
        }

        // このセッション向けのLOGIN_STATUS行(有効期限内・正しい遷移状態)が見つからない場合は
        // 有効期限切れとして扱う(他セッションのパスコードを使い回すケースもここに含まれる)
        Optional<LinkedHashMap<String, String>> loginStatus =
                loginStatusService.findForVerification(accountId, sessionId);
        if (loginStatus.isEmpty()) {
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId,
                    TWO_FACTOR_EXPIRED_ERROR_GNR_KEY_VAL_ID);
            return writeAsString(buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true));
        }

        String loginStatusId = loginStatus.get().get("LOGIN_STATUS_ID");
        String passcodeHash = loginStatus.get().get("PASSCODE_HASH");

        if (passwordEncoder.matches(inputCode, passcodeHash)) {
            return writeAsString(buildSuccessResponse(sessionId, accountId));
        }
        return writeAsString(buildFailureResponse(sessionId, accountId, loginStatusId));
    }

    private ObjectNode buildLockedResponse(String sessionId, String accountId) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID);
        return buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true);
    }

    private ObjectNode buildSuccessResponse(String sessionId, String accountId) {

        // 認証成功。役目を終えたLOGIN_STATUS/ACCNT_AUTH_LOCKの行はいずれも物理削除する
        loginStatusService.deleteFor(accountId, sessionId);
        accntAuthLockService.deleteForAccount(accountId);

        ObjectNode output = buildRedirectResponse("/taskall-v2/service/myPage.html", true);
        ObjectNode accountRow = objectMapper.createObjectNode();
        accountRow.put("ACCNT_ID", accountId);
        output.putArray("account").add(accountRow);
        return output;
    }

    private ObjectNode buildFailureResponse(String sessionId, String accountId, String loginStatusId) {

        // 二次認証失敗を記録し、アカウント全体の失敗回数へ合算する
        loginStatusService.markSecondAuthFail(loginStatusId);
        accntAuthLockService.recordFailure(accountId);

        if (accntAuthLockService.isLocked(accountId)) {
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID);
            return buildRedirectResponse("/taskall-v2/service/top.html?errMsgKey=" + errMsgKey, true);
        }

        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, TWO_FACTOR_CODE_ERROR_GNR_KEY_VAL_ID);
        return buildRedirectResponse("/taskall-v2/service/twoFactorAuth.html?errMsgKey=" + errMsgKey, false);
    }

    private ObjectNode buildRedirectResponse(String destination, boolean twoFactorAuthCompleted) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", destination);
        output.put("twoFactorAuthCompleted", twoFactorAuthCompleted);
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
