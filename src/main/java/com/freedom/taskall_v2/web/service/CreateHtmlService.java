package com.freedom.taskall_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.common.util.VariablePlaceholderResolver;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * リクエストURIに対応する画面パーツ・画面表示項目を取得し、応答種別・遷移先を決定するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code CreateHtmlService}に相当します。移植元は{@code ITEM_KEY}を
 * トップレベルのキーとして展開するため同一画面に同じパーツが複数あると衝突しますが、本移植では
 * {@code htmlPage}配列の各要素({@code PARTS_IN_PAGE_ID}単位)に画面表示項目を{@code items}配列
 * としてネストする構造に変更しています。
 * </p>
 */
@Service
public class CreateHtmlService implements ScriptElementService {

    private static final String PAGE_SQL = """
            SELECT
                A.PARTS_IN_PAGE_ID, B.HTML_PAGE_ID, B.PAGE_NAME, B.RESP_KIND_GET,
                B.RESP_KIND_POST, B.RESP_KIND_PUT, B.RESP_KIND_DELETE,
                B.DESTINATION_GET, B.DESTINATION_POST, B.DESTINATION_PUT,
                B.DESTINATION_DELETE, C.HTML_PARTS_ID, C.PARTS_NAME,
                D.PARTS_ITEM_ID, D.ITEM_KEY, D.ITEM_QUERY
            FROM PARTS_IN_PAGE A
            LEFT JOIN HTML_PAGE B ON A.HTML_PAGE_ID = B.HTML_PAGE_ID
            LEFT JOIN HTML_PARTS C ON A.HTML_PARTS_ID = C.HTML_PARTS_ID
            LEFT JOIN PARTS_ITEM D ON A.PARTS_IN_PAGE_ID = D.PARTS_IN_PAGE_ID
            LEFT JOIN URI_PATTERN E ON B.URI_PATTERN_ID = E.URI_PATTERN_ID
            WHERE E.URI_PATTERN = ?
            ORDER BY A.ORD_IN_GRP, D.ORD_IN_GRP
            """;

    private final RecordQueryService recordQueryService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public CreateHtmlService(RecordQueryService recordQueryService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // 入力JSONから画面生成に必要なリクエスト情報を取り出して処理の起点にする
        ObjectNode context = readAsObjectNode(contextJson);

        String requestUri = context.path("requestUri").asString("");
        String requestKind = context.path("requestKind").asString("");

        // errMsgKeyはPARTS_ITEM.ITEM_QUERY(例: errMsgList)のプレースホルダとして参照されるが、
        // ログイン失敗などPRGパターンを経由しない通常の画面表示では入力JSONに含まれない。
        // 未設定のままだとVariablePlaceholderResolverの解決に失敗するため、
        // 「エラー無し」を意味するデフォルト値"0"を補っておく。
        if (context.path("errMsgKey").asString("").isBlank()) {
            context.put("errMsgKey", "0");
        }

        // noticeKeyも同様にPARTS_ITEM.ITEM_QUERY(noticeList)のプレースホルダとして参照されるが、
        // サインアップ完了などのPRGパターンを経由しない通常の画面表示では入力JSONに含まれない。
        // 未設定のままだとVariablePlaceholderResolverの解決に失敗するため、
        // 「通知無し」を意味するデフォルト値"0"を補っておく。
        if (context.path("noticeKey").asString("").isBlank()) {
            context.put("noticeKey", "0");
        }

        List<LinkedHashMap<String, String>> pageRows = recordQueryService.select(PAGE_SQL, List.of(requestUri));
        if (pageRows.isEmpty()) {
            throw new ApplicationInternalException(msg.get("msg.err.web.pageNotFound", requestUri));
        }

        // 画面パーツ群をhtmlPageへ構築し、後続のビュー解決に必要な応答情報も出力へまとめる
        ObjectNode output = objectMapper.createObjectNode();
        output.set("htmlPage", buildHtmlPage(pageRows, context));

        // respKind/destinationは通常HTML_PAGEの定義値から決定するが、LoginServiceのように
        // 同一SCR内で先行実行されるサービスがPRGパターン(認証失敗時のリダイレクト等)により
        // 既に入力JSONへrespKind/destinationを設定済みの場合がある。その場合は本サービスの
        // デフォルト値で上書きせず、先行サービスが決定した値をそのまま後続へ引き継ぐ。
        String existingRespKind = context.path("respKind").asString("");
        output.put("respKind", existingRespKind.isBlank()
                ? pageRows.get(0).get("RESP_KIND_" + requestKind)
                : existingRespKind);

        String existingDestination = context.path("destination").asString("");
        output.put("destination", existingDestination.isBlank()
                ? VariablePlaceholderResolver.resolve(pageRows.get(0).get("DESTINATION_" + requestKind), context, msg)
                : existingDestination);

        return writeAsString(output);
    }

    private ArrayNode buildHtmlPage(List<LinkedHashMap<String, String>> pageRows, ObjectNode context) {

        // PARTS_IN_PAGE単位で画面パーツを束ねるための配列と検索用マップを初期化する
        ArrayNode htmlPage = objectMapper.createArrayNode();
        Map<String, ObjectNode> partsInPageById = new LinkedHashMap<>();

        for (LinkedHashMap<String, String> row : pageRows) {

            String partsInPageId = row.get("PARTS_IN_PAGE_ID");
            ObjectNode partsInPage = partsInPageById.get(partsInPageId);
            if (partsInPage == null) {
                // 初出のPARTS_IN_PAGE_IDごとに画面パーツの枠を生成し、戻り値配列へ登録する
                partsInPage = objectMapper.createObjectNode();
                partsInPage.put("partsInPageId", partsInPageId);
                partsInPage.put("htmlPartsId", row.get("HTML_PARTS_ID"));
                partsInPage.put("partsName", row.get("PARTS_NAME"));
                partsInPage.set("items", objectMapper.createArrayNode());
                partsInPageById.put(partsInPageId, partsInPage);
                htmlPage.add(partsInPage);
            }

            String itemQuery = row.get("ITEM_QUERY");
            if (itemQuery != null && !itemQuery.isBlank()) {
                // 各画面表示項目のクエリを実行し、対象パーツ配下のitems配列へネストして保持する
                ObjectNode item = objectMapper.createObjectNode();
                item.put("itemKey", row.get("ITEM_KEY"));
                item.set("records", selectItem(itemQuery, context));
                ((ArrayNode) partsInPage.get("items")).add(item);
            }
        }

        return htmlPage;
    }

    private ArrayNode selectItem(String itemQuery, ObjectNode context) {

        // プレースホルダを解決した項目クエリを実行し、画面表示用レコード一覧を取得する
        String sql = VariablePlaceholderResolver.resolve(itemQuery, context, msg);
        List<LinkedHashMap<String, String>> recordList = recordQueryService.select(sql);

        // SELECT結果をJSON配列へ詰め替えつつ、値側に残るプレースホルダも画面表示用に解決する
        ArrayNode records = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> record : recordList) {

            // リクエスト全体のcontextに、その行自身の列値を重ね合わせた行単位のcontextを作る。
            // これにより、URI_PATTERNにテーブル名等の行データを埋め込んだ#{TABLE_NAME}のような
            // プレースホルダーを、行ごとに異なる値で解決できる(物理カラム名は常に大文字+アンダー
            // バーのため、camelCase/lowerCamelCaseのcontextキーと衝突する心配はない)。
            ObjectNode recordContext = context.deepCopy();
            record.forEach(recordContext::put);

            ObjectNode recordNode = objectMapper.createObjectNode();
            for (Map.Entry<String, String> entry : record.entrySet()) {
                recordNode.put(entry.getKey(),
                        VariablePlaceholderResolver.resolve(entry.getValue(), recordContext, msg));
            }
            records.add(recordNode);
        }
        return records;
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
