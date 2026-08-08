package com.freedom.taskall_v2.web.controller;

import java.util.Enumeration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * URIパターンに基づいてDBレコード駆動でリクエストを処理するコントローラです。
 *
 * <p>
 * 移植元「remainz」の{@code ServiceControlServlet}に相当します。実際の業務ロジックの実行は
 * {@link RequestHandlingService}に委譲し、本クラスはリクエストコンテキストの構築とビュー解決のみを
 * 担当します。
 * </p>
 */
@Controller
public class TaskallV2Controller {

    private static final Logger logger = LoggerFactory.getLogger(TaskallV2Controller.class);

    private final RequestHandlingService requestHandlingService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public TaskallV2Controller(RequestHandlingService requestHandlingService, ObjectMapper objectMapper,
            MsgUtil msg) {
        this.requestHandlingService = requestHandlingService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    // EC2側のリリーススクリプト(release.sh)がデプロイ直後にアプリの生存確認を行うための
    // 専用エンドポイントです(issue #51)。DBレコード駆動のhandleRequest(URI_PATTERN等)へは
    // あえて委譲せず、DB/業務ロジックに一切依存しない固定応答のみを返します。これは、
    // 本エンドポイントがヘルスチェック専用であり、DB未初期化・業務ロジックの不具合等とは
    // 独立してアプリプロセス自体の生死のみを判定したいためです(認証も不要のため
    // SecurityConfigのpermitAll対象にも含まれます)。
    @GetMapping("/healthz")
    @ResponseBody
    public ResponseEntity<String> getHealthz() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/taskall-v2/service/top.html")
    public String getTop(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/myPage.html")
    public String getMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/twoFactorAuth.html")
    public String getTwoFactorAuth(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/twoFactorAuth.html")
    public String postTwoFactorAuth(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    // 以下、DBメンテナンス機能の画面群。いずれもDBレコード駆動の汎用処理(handleRequest)へ
    // 委譲するのみで、画面固有の業務ロジックはコントローラ側に持たない
    @GetMapping("/taskall-v2/service/dbMainte.html")
    public String getDbMainte(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/tableDefRef.html")
    public String getTableDefRef(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/tableDataMainte.html")
    public String getTableDataMainte(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/tableDataMainte.html")
    public String postTableDataMainte(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/tableDataMainte/newRecord.html")
    public String getNewRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/tableDataMainte/newRecord.html")
    public String postNewRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/tableDataMainte/editRecord.html")
    public String getEditRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/tableDataMainte/editRecord.html")
    public String postEditRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/tableDataMainte/deleteRecord.html")
    public String getDeleteRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/tableDataMainte/deleteRecord.html")
    public String postDeleteRecord(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/recordRef.html")
    public String getRecordRef(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/updateDbConfirm.html")
    public String getUpdateDbConfirm(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/updateDbConfirm.html")
    public String postUpdateDbConfirm(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/getDbConfirm.html")
    public String getGetDbConfirm(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/taskall-v2/service/getDbConfirm.html")
    public String postGetDbConfirm(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    @GetMapping("/taskall-v2/service/updateDbComplete.html")
    public String getUpdateDbComplete(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/taskall-v2/service/getDbComplete.html")
    public String getGetDbComplete(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    private String handleRequest(HttpServletRequest request, String requestKind, Model model) {

        // 受信リクエストの内容を記録し、業務サービスへ渡す共通コンテキストを組み立てる
        logRequestInfo(request);

        ObjectNode context = buildContext(request, requestKind);
        JsonNode result = readAsObjectNode(requestHandlingService.execute(writeAsString(context)));

        // 実行結果からセッションとModelを更新し、最後にレスポンス種別に応じたビュー名へ変換する
        storeAccountIdIfExists(request.getSession(), result);
        clearPendingTwoFactorAccountIdIfCompleted(request.getSession(), result);
        populateModel(result, model);

        model.addAttribute("pendingTwoFactorAccountId", request.getSession().getAttribute("pendingTwoFactorAccountId"));

        return resolveViewName(result);
    }

    private void logRequestInfo(HttpServletRequest request) {

        // リクエスト属性を収集し、サーバ内で付与された値も含めて追跡できるようにする
        // ただしSpringFramework内部で付与される属性はログを見ても有益にならないノイズのため除外する
        StringBuilder log = new StringBuilder();
        log.append("[Attributes]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getAttributeNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            if (name.contains("springframework")) {
                continue;
            }
            log.append('\t').append(name).append(": ").append(request.getAttribute(name))
                    .append(System.lineSeparator());
        }

        // リクエストヘッダを収集し、クライアントやプロキシ経由の差異を確認しやすくする
        log.append("[Headers]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            log.append('\t').append(name).append(": ").append(request.getHeader(name))
                    .append(System.lineSeparator());
        }

        // リクエストパラメータを収集し、機微情報はマスクしたうえで入力内容を記録する
        log.append("[Parameters]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = "PASSWORD".equals(name) ? "*****" : request.getParameter(name);
            log.append('\t').append(name).append(": ").append(value).append(System.lineSeparator());
        }

        logger.info(log.toString());
    }

    private ObjectNode buildContext(HttpServletRequest request, String requestKind) {

        // リクエストパラメータをそのまま入力コンテキストへ転記し、後続サービスの入力値を揃える
        ObjectNode context = objectMapper.createObjectNode();

        for (Enumeration<String> names = request.getParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            context.put(name, request.getParameter(name));
        }

        // ページング用パラメータ(limit/offset)は、初回アクセス時にリンクへ付与されないことがあるため、
        // 未指定の場合はここで既定値を補う(#{limit}/#{offset}のようなSQLプレースホルダーが
        // 未解決のまま実行され、SQLエラーになることを防ぐ)
        if (context.get("limit") == null) {
            context.put("limit", "10");
        }
        if (context.get("offset") == null) {
            context.put("offset", "0");
        }

        // セッション保持中のアカウントIDと、今回のリクエストを識別する共通メタ情報を設定する
        String accountId = (String) request.getSession().getAttribute("accountId");
        if (accountId != null) {
            context.put("accountId", accountId);
        }

        // 二段階認証(一次認証通過・二次認証待ち)中のアカウントIDをセッションから引き継ぐ
        Object pendingTwoFactorAccountId = request.getSession().getAttribute("pendingTwoFactorAccountId");
        if (pendingTwoFactorAccountId != null) {
            context.put("pendingTwoFactorAccountId", (String) pendingTwoFactorAccountId);
        }

        context.put("requestKind", requestKind);
        context.put("requestUri", request.getRequestURI());
        context.put("sessionId", request.getSession().getId());

        return context;
    }

    private void storeAccountIdIfExists(HttpSession session, JsonNode result) {

        JsonNode account = result.path("account");
        if (!account.isArray() || account.isEmpty()) {
            return;
        }

        session.setAttribute("accountId", account.get(0).path("ACCNT_ID").asString());
    }

    private void clearPendingTwoFactorAccountIdIfCompleted(HttpSession session, JsonNode result) {
        if (result.path("twoFactorAuthCompleted").asBoolean(false)) {
            session.removeAttribute("pendingTwoFactorAccountId");
        }
    }

    private void populateModel(JsonNode result, Model model) {
        Map<String, Object> attributes = objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {
        });
        attributes.forEach(model::addAttribute);
    }

    private String resolveViewName(JsonNode result) {

        // リダイレクト応答が要求されている場合は、Spring MVC向けのredirectプレフィックスを付ける
        String respKind = result.path("respKind").asString("");
        String destination = result.path("destination").asString("");

        if ("redirect".equals(respKind)) {
            return "redirect:" + destination;
        }

        // テンプレート解決時は.html拡張子を除去し、Thymeleafのビュー名に合わせる
        return destination.endsWith(".html") ? destination.substring(0, destination.length() - ".html".length())
                : destination;
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
