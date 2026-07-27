package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptExecutionService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * リクエストURI・リクエスト種別からスクリプトIDを解決し、{@link ScriptExecutionService}に処理を委譲する
 * 共通処理クラスです。
 *
 * <p>
 * 移植元「remainz」の{@code ServiceControlServlet#controllService}/{@code AnalyzeUriService}に
 * 相当します。commit/rollbackは本メソッドに付与する{@link Transactional}でSpring管理とするため、
 * 明示的なcommit/rollback処理は行いません。
 * </p>
 */
@Service
public class RequestHandlingService {

    private static final String HTML_PAGE_SQL = """
            SELECT
                A.HTML_PAGE_ID, A.PAGE_NAME, A.SCR_ID_GET, A.SCR_ID_POST,
                A.SCR_ID_PUT, A.SCR_ID_DELETE
            FROM HTML_PAGE A
            LEFT JOIN URI_PATTERN B ON A.URI_PATTERN_ID = B.URI_PATTERN_ID
            WHERE B.URI_PATTERN = ?
            """;

    private final RecordQueryService recordQueryService;
    private final ScriptExecutionService scriptExecutionService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public RequestHandlingService(RecordQueryService recordQueryService,
            ScriptExecutionService scriptExecutionService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.scriptExecutionService = scriptExecutionService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    /**
     * リクエストURI・リクエスト種別に対応するスクリプトを実行します。
     *
     * @param contextJson {@code requestKind}/{@code requestUri}を含むリクエストコンテキストのJSON文字列
     * @return スクリプト実行後のコンテキストを表すJSON文字列
     */
    @Transactional
    public String execute(String contextJson) {

        // 入力JSONからリクエスト種別とURIを取り出し、以降の解決処理に利用する
        ObjectNode context = readAsObjectNode(contextJson);
        String requestKind = context.path("requestKind").asString("");
        String requestUri = context.path("requestUri").asString("");

        // リクエスト内容に対応するスクリプトIDを解決し、実際の処理実行を共通サービスへ委譲する
        String scriptId = resolveScriptId(requestKind, requestUri);

        return scriptExecutionService.execute(scriptId, contextJson);
    }

    private String resolveScriptId(String requestKind, String requestUri) {

        // URIに対応するHTML_PAGEを一意に取得できることを確認し、スクリプト解決の前提を満たす
        List<LinkedHashMap<String, String>> pageRows = recordQueryService.select(HTML_PAGE_SQL, List.of(requestUri));
        if (pageRows.size() != 1) {
            throw new ApplicationInternalException(msg.get("msg.err.web.invalidRequestUri", requestUri));
        }

        // リクエスト種別に対応するスクリプトIDを取得し、未定義のHTTPメソッド呼び出しを弾く
        String scriptId = pageRows.get(0).get("SCR_ID_" + requestKind);
        if (scriptId == null || "0".equals(scriptId)) {
            throw new ApplicationInternalException(
                    msg.get("msg.err.web.invalidRequestKind", requestUri, requestKind));
        }

        return scriptId;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }
}
