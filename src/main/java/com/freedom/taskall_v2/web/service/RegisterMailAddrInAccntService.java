package com.freedom.taskall_v2.web.service;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.service.mail.MailAddrEncryptionService;
import com.freedom.taskall_v2.common.service.mail.MailboxAccessVerifier;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 監視対象メールアドレス登録画面(issue #96)の「登録」ボタン押下を処理するサービスです。
 *
 * <p>
 * 入力されたメールアドレス・パスワードで実際にメールボックスへ接続できるかを確認し、
 * 接続できた場合のみ暗号化した上で{@code MAIL_ADDR_IN_ACCNT}へ登録(新規登録・再登録どちらも
 * UPDATE/INSERTの1メソッドで吸収)する。接続確認に失敗した場合は、既存の登録内容を変更せず、
 * エラーメッセージ付きで登録画面へ戻す(issue #96のユーザ回答による方針)。
 * </p>
 */
@Service
public class RegisterMailAddrInAccntService implements ScriptElementService {

    private static final String MAIL_ADDR_REGISTER_DESTINATION = "/taskall-v2/service/mailAddrRegister.html";
    private static final String MY_PAGE_DESTINATION = "/taskall-v2/service/myPage.html";
    private static final String CONNECTION_ERROR_GNR_KEY_VAL_ID = "1000601";

    private final MailboxAccessVerifier mailboxAccessVerifier;
    private final MailAddrEncryptionService mailAddrEncryptionService;
    private final MailAddrInAccntService mailAddrInAccntService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public RegisterMailAddrInAccntService(MailboxAccessVerifier mailboxAccessVerifier,
            MailAddrEncryptionService mailAddrEncryptionService, MailAddrInAccntService mailAddrInAccntService,
            ErrMsgService errMsgService, ObjectMapper objectMapper, MsgUtil msg) {
        this.mailboxAccessVerifier = mailboxAccessVerifier;
        this.mailAddrEncryptionService = mailAddrEncryptionService;
        this.mailAddrInAccntService = mailAddrInAccntService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String accountId = require(context, "accountId");
        String mailAddress = require(context, "MAIL_ADDR");
        String password = require(context, "PASSWORD");

        // 実際にメールボックスへ接続できるか確認し、読めない場合はエラー付きで登録画面へ戻す(既存行は変更しない)
        if (!mailboxAccessVerifier.canAccess(mailAddress, password)) {
            String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, CONNECTION_ERROR_GNR_KEY_VAL_ID);
            return writeAsString(
                    buildRedirect(MAIL_ADDR_REGISTER_DESTINATION + "?errMsgKey=" + errMsgKey));
        }

        // 接続確認できた場合のみ、パスワードを暗号化して登録(新規/再登録いずれもupsertで吸収)する
        String encryptedPassword = mailAddrEncryptionService.encrypt(password);
        mailAddrInAccntService.upsert(accountId, mailAddress, encryptedPassword);

        return writeAsString(buildRedirect(MY_PAGE_DESTINATION));
    }

    private ObjectNode buildRedirect(String destination) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", destination);
        return output;
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
