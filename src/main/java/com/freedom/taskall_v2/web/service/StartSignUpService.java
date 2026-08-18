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
 * サインアップの1画面目（メールアドレス・アカウント名・希望パスワード入力）を処理するサービスです。
 */
@Service
public class StartSignUpService implements ScriptElementService {

    private static final String GUEST_ACCOUNT_ID = "1000001";
    private static final String ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID = "1000402";
    private static final String PASSWORD_CONFIRM_ERROR_GNR_KEY_VAL_ID = "1000406";
    private static final String PASSWORD_STRENGTH_ERROR_GNR_KEY_VAL_ID = "1000407";
    private static final String MAIL_EXISTS_ERROR_GNR_KEY_VAL_ID = "1000408";

    private static final String PERSONAL_APROLE_ID = "1000101";
    private static final String CORPORATE_APROLE_ID = "1000201";

    private final SignUpService signUpService;
    private final PasswordStrengthValidator passwordStrengthValidator;
    private final PasswordEncoder passwordEncoder;
    private final PasscodeGenerator passcodeGenerator;
    private final SignUpMailService signUpMailService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public StartSignUpService(SignUpService signUpService, PasswordStrengthValidator passwordStrengthValidator,
            PasswordEncoder passwordEncoder, PasscodeGenerator passcodeGenerator, SignUpMailService signUpMailService,
            ErrMsgService errMsgService, ObjectMapper objectMapper, MsgUtil msg) {
        this.signUpService = signUpService;
        this.passwordStrengthValidator = passwordStrengthValidator;
        this.passwordEncoder = passwordEncoder;
        this.passcodeGenerator = passcodeGenerator;
        this.signUpMailService = signUpMailService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        String sessionId = require(context, "sessionId");
        String mailAddress = require(context, "MAIL_ADDRESS").toLowerCase();
        String accountName = require(context, "ACCOUNT_NAME");
        String password = require(context, "PASSWORD");
        String passwordConfirm = require(context, "PASSWORD_CONFIRM");
        String accountKind = require(context, "ACCOUNT_KIND");

        // 改ざんされた個人/法人区分(1・2以外)を検知した場合は、エラーを出さずTOPへ黙って戻す
        String aproleId = resolveAproleId(accountKind);
        if (aproleId == null) {
            return writeAsString(buildTopRedirect());
        }

        // 入力チェックに失敗した場合は、同一画面へエラーメッセージ付きで戻す
        if (!password.equals(passwordConfirm)) {
            return writeAsString(buildErrRedirect(sessionId, "/taskall-v2/service/signUp.html",
                    PASSWORD_CONFIRM_ERROR_GNR_KEY_VAL_ID));
        }
        if (!passwordStrengthValidator.isValid(password)) {
            return writeAsString(buildErrRedirect(sessionId, "/taskall-v2/service/signUp.html",
                    PASSWORD_STRENGTH_ERROR_GNR_KEY_VAL_ID));
        }

        // 既にアカウントが存在するメールアドレスは登録できないため、マイページへエラー付きで戻す
        if (signUpService.findAccountByMailAddress(mailAddress).isPresent()) {
            return writeAsString(buildErrRedirect(sessionId, "/taskall-v2/service/myPage.html",
                    MAIL_EXISTS_ERROR_GNR_KEY_VAL_ID));
        }

        // 同一メールアドレスの既存サインアップ行があれば、ロック中(かつ期限内)のみエラー、それ以外は削除して再受付する
        List<LinkedHashMap<String, String>> existingRows = signUpService.findByMailAddress(mailAddress);
        boolean hasActiveLock = existingRows.stream()
                .anyMatch(row -> signUpService.isLocked(row) && !signUpService.isExpired(row));
        if (hasActiveLock) {
            return writeAsString(buildErrRedirect(sessionId, "/taskall-v2/service/signUp.html",
                    ACCOUNT_LOCK_ERROR_GNR_KEY_VAL_ID));
        }
        if (!existingRows.isEmpty()) {
            signUpService.deleteByMailAddress(mailAddress);
        }

        // 新しいサインアップ行を作成し、6桁コードをメール送信したら次画面へ遷移させる
        String passcode = passcodeGenerator.generate();
        String signUpId = signUpService.create(sessionId, aproleId, mailAddress, accountName,
                passwordEncoder.encode(password), passwordEncoder.encode(passcode));
        try {
            signUpMailService.sendPasscode(mailAddress, passcode);
        } catch (ApplicationInternalException e) {
            signUpService.deleteById(signUpId);
            throw e;
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/signUpPasscode.html");
        output.put("pendingSignUpId", signUpId);
        return writeAsString(output);
    }

    private String resolveAproleId(String accountKind) {
        if ("1".equals(accountKind)) {
            return PERSONAL_APROLE_ID;
        }
        if ("2".equals(accountKind)) {
            return CORPORATE_APROLE_ID;
        }
        return null;
    }

    private String require(ObjectNode context, String fieldName) {
        String value = context.path(fieldName).asString("");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.warn.web.requiredParamMissing", fieldName));
        }
        return value;
    }

    private ObjectNode buildTopRedirect() {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", "/taskall-v2/service/top.html");
        return output;
    }

    private ObjectNode buildErrRedirect(String sessionId, String destination, String gnrKeyValId) {
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, GUEST_ACCOUNT_ID, gnrKeyValId);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("respKind", "redirect");
        output.put("destination", destination + "?errMsgKey=" + errMsgKey);
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
