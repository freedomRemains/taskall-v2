package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ログイン認証を行うサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.LoginService}に相当します。認証失敗時は
 * 例外をスローせず、PRG(Post/Redirect/Get)パターンにより、{@code respKind=redirect}、
 * {@code destination=myPage.html?errMsgKey=<キー>}を出力JSONに設定して正常終了します。これは
 * UI遷移制御のための意図的な設計であり、通常の例外方針(業務/システム例外)の対象外とします。
 * </p>
 */
@Service
public class LoginService implements ScriptElementService {

    /** ログイン失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String LOGIN_ERROR_GNR_KEY_VAL_ID = "1000401";

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS, A.PASSWORD,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.MAIL_ADDRESS = ?
            """;

    private final RecordQueryService recordQueryService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public LoginService(RecordQueryService recordQueryService, ErrMsgService errMsgService,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // 入力JSONから認証に必要な値を取り出し、必須パラメータ不足は業務エラーとして扱う
        ObjectNode context = readAsObjectNode(contextJson);

        String mailAddress = context.path("MAIL_ADDRESS").asString("");
        if (mailAddress.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "MAIL_ADDRESS"));
        }
        String password = context.path("PASSWORD").asString("");
        if (password.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "PASSWORD"));
        }

        // 認証成功時はアカウントIDのみを返し、失敗時はPRG用のリダイレクト情報を組み立てる
        ObjectNode output = objectMapper.createObjectNode();

        String authenticatedAccountId = authenticate(mailAddress, password);
        if (authenticatedAccountId != null) {
            output.put("accountId", authenticatedAccountId);
            return writeAsString(output);
        }

        String sessionId = context.path("sessionId").asString("");
        String accountId = context.path("accountId").asString("");
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, LOGIN_ERROR_GNR_KEY_VAL_ID);

        output.put("respKind", "redirect");
        output.put("destination", "myPage.html?errMsgKey=" + errMsgKey);

        return writeAsString(output);
    }

    private String authenticate(String mailAddress, String password) {

        // メールアドレスに対応するアカウントを取得し、認証対象が一意に定まる場合のみ後続判定へ進める
        List<LinkedHashMap<String, String>> accountRows =
                recordQueryService.select(ACCOUNT_SQL, List.of(mailAddress));
        if (accountRows.size() != 1) {
            return null;
        }

        // TODO ハッシュ化した値を比較する
        if (!password.equals(accountRows.get(0).get("PASSWORD"))) {
            return null;
        }

        return accountRows.get(0).get("ACCNT_ID");
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
