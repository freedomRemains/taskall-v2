package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CreateHtmlService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class CreateHtmlServiceTest {

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

    @Mock
    private RecordQueryService recordQueryService;

    private CreateHtmlService createHtmlService;

    @BeforeEach
    void setUp() {
        createHtmlService = new CreateHtmlService(recordQueryService, JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> pageRow(String partsInPageId, String htmlPartsId, String partsName,
            String itemKey, String itemQuery) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("PARTS_IN_PAGE_ID", partsInPageId);
        row.put("HTML_PAGE_ID", "1000001");
        row.put("PAGE_NAME", "TOP");
        row.put("RESP_KIND_GET", "forward");
        row.put("RESP_KIND_POST", null);
        row.put("RESP_KIND_PUT", null);
        row.put("RESP_KIND_DELETE", null);
        row.put("DESTINATION_GET", "10000_contents");
        row.put("DESTINATION_POST", null);
        row.put("DESTINATION_PUT", null);
        row.put("DESTINATION_DELETE", null);
        row.put("HTML_PARTS_ID", htmlPartsId);
        row.put("PARTS_NAME", partsName);
        row.put("PARTS_ITEM_ID", "1000001");
        row.put("ITEM_KEY", itemKey);
        row.put("ITEM_QUERY", itemQuery);
        return row;
    }

    @Test
    void 同一画面に複数パーツがある場合にitemsがパーツ単位でネストされること() {

        LinkedHashMap<String, String> systemNameRow =
                pageRow("1000001", "1000001", "システム名", "systemName",
                        "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'");
        LinkedHashMap<String, String> urlLinkRow =
                pageRow("1000002", "1000002", "共通ヘッダ", "urlLink",
                        "SELECT PAGE_NAME, URI_PATTERN FROM HTML_PAGE");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/top.html"))))
                .thenReturn(new ArrayList<>(List.of(systemNameRow, urlLinkRow)));

        LinkedHashMap<String, String> systemNameRecord = new LinkedHashMap<>();
        systemNameRecord.put("GNR_VAL", "Taskall");
        when(recordQueryService.select(eq("SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'")))
                .thenReturn(new ArrayList<>(List.of(systemNameRecord)));

        LinkedHashMap<String, String> urlLinkRecord = new LinkedHashMap<>();
        urlLinkRecord.put("PAGE_NAME", "TOP");
        urlLinkRecord.put("URI_PATTERN", "/taskall-v2/service/top.html");
        when(recordQueryService.select(eq("SELECT PAGE_NAME, URI_PATTERN FROM HTML_PAGE")))
                .thenReturn(new ArrayList<>(List.of(urlLinkRecord)));

        String result = createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/top.html\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("forward");
        assertThat(node.path("destination").asString()).isEqualTo("10000_contents");
        assertThat(node.path("htmlPage")).hasSize(2);
        assertThat(node.path("htmlPage").get(0).path("partsInPageId").asString()).isEqualTo("1000001");
        assertThat(node.path("htmlPage").get(0).path("items").get(0).path("itemKey").asString())
                .isEqualTo("systemName");
        assertThat(node.path("htmlPage").get(0).path("items").get(0).path("records").get(0).path("GNR_VAL")
                .asString()).isEqualTo("Taskall");
        assertThat(node.path("htmlPage").get(1).path("items").get(0).path("records").get(0).path("URI_PATTERN")
                .asString()).isEqualTo("/taskall-v2/service/top.html");
    }

    @Test
    void 項目クエリの結果に含まれるプレースホルダーが置換されること() {

        LinkedHashMap<String, String> row = pageRow("1000001", "1000001", "システム名", "tableName",
                "SELECT FIELD_NAME FROM TBL_DEF");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/detail.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        LinkedHashMap<String, String> record = new LinkedHashMap<>();
        record.put("FIELD_NAME", "#{tableName}のフィールド");
        when(recordQueryService.select(eq("SELECT FIELD_NAME FROM TBL_DEF")))
                .thenReturn(new ArrayList<>(List.of(record)));

        String result = createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/detail.html\","
                        + "\"tableName\":\"ACCNT\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("htmlPage").get(0).path("items").get(0).path("records").get(0).path("FIELD_NAME")
                .asString()).isEqualTo("ACCNTのフィールド");
    }

    @Test
    void 項目クエリの結果の別列の値を使って行単位でプレースホルダーが解決されること() {

        LinkedHashMap<String, String> row = pageRow("1000303", "1000401", "テーブル一覧", "tableList",
                "SELECT TABLE_NAME, URL_TEMPLATE FROM TBL_DEF");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/dbMainte.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        // 同じ行のTABLE_NAME列の値が、同じ行の別列に残るプレースホルダーの解決に使われることを確認する
        LinkedHashMap<String, String> accntRecord = new LinkedHashMap<>();
        accntRecord.put("TABLE_NAME", "ACCNT");
        accntRecord.put("URL_TEMPLATE", "/taskall-v2/service/tableDefRef.html?tableName=#{TABLE_NAME}");
        LinkedHashMap<String, String> tblDefRecord = new LinkedHashMap<>();
        tblDefRecord.put("TABLE_NAME", "TBL_DEF");
        tblDefRecord.put("URL_TEMPLATE", "/taskall-v2/service/tableDefRef.html?tableName=#{TABLE_NAME}");
        when(recordQueryService.select(eq("SELECT TABLE_NAME, URL_TEMPLATE FROM TBL_DEF")))
                .thenReturn(new ArrayList<>(List.of(accntRecord, tblDefRecord)));

        String result = createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/dbMainte.html\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        JsonNode records = node.path("htmlPage").get(0).path("items").get(0).path("records");
        assertThat(records.get(0).path("URL_TEMPLATE").asString())
                .isEqualTo("/taskall-v2/service/tableDefRef.html?tableName=ACCNT");
        assertThat(records.get(1).path("URL_TEMPLATE").asString())
                .isEqualTo("/taskall-v2/service/tableDefRef.html?tableName=TBL_DEF");
    }

    @Test
    void リクエストURIに対応するページ定義が存在しない場合は例外がスローされること() {

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/unknown.html"))))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/unknown.html\"}"))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void errMsgKeyが未指定の場合は0がデフォルト設定されプレースホルダーが解決されること() {

        LinkedHashMap<String, String> row = pageRow("1000203", "1001101", "エラーメッセージ一覧領域",
                "errMsgList", "SELECT ERR_MSG FROM ERR_MSG WHERE ERR_MSG_ID = #{errMsgKey}");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/myPage.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));
        when(recordQueryService.select(eq("SELECT ERR_MSG FROM ERR_MSG WHERE ERR_MSG_ID = 0")))
                .thenReturn(new ArrayList<>());

        String result = createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/myPage.html\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("htmlPage").get(0).path("items").get(0).path("records")).isEmpty();
    }

    @Test
    void respKindとdestinationがコンテキストに既に存在する場合は上書きしないこと() {

        LinkedHashMap<String, String> row =
                pageRow("1000201", "1000001", "システム名", "systemName",
                        "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'");
        row.put("RESP_KIND_POST", "redirect");
        row.put("DESTINATION_POST", "myPage.html");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/taskall-v2/service/myPage.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        LinkedHashMap<String, String> systemNameRecord = new LinkedHashMap<>();
        systemNameRecord.put("GNR_VAL", "Taskall");
        when(recordQueryService.select(eq("SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'")))
                .thenReturn(new ArrayList<>(List.of(systemNameRecord)));

        String result = createHtmlService.execute(
                "{\"requestKind\":\"POST\",\"requestUri\":\"/taskall-v2/service/myPage.html\","
                        + "\"respKind\":\"redirect\",\"destination\":\"myPage.html?errMsgKey=5\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
        assertThat(node.path("destination").asString()).isEqualTo("myPage.html?errMsgKey=5");
    }
}
