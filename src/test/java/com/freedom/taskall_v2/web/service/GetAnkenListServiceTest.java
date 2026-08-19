package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link GetAnkenListService}のテストです。
 *
 * <p>
 * {@link RecordQueryService#select(String, List)}呼び出しは、SQL文に含まれるテーブル名等の
 * 特徴的な文字列で振り分けたスタブ応答を返すようにし、属性グループ/属性一覧・案件一覧・件数・
 * 言語集約の各SQLがそれぞれ想定通りの結果を組み立てられることを検証する。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class GetAnkenListServiceTest {

    @Mock
    private RecordQueryService recordQueryService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private GetAnkenListService getAnkenListService;

    @BeforeEach
    void setUp() {
        getAnkenListService = new GetAnkenListService(recordQueryService, jsonMapper, new MsgUtil());
    }

    @Test
    void 属性未選択の場合は公開かつ募集中の案件一覧が全件対象として返却されること() {

        stubRecordQuery(2L, List.of(ankenRow("1000001", "東京", "案件A", "説明A", "60万円", "3ヶ月")),
                List.of(languageRow("1000001", "Java")));

        String result = getAnkenListService.execute("{\"limit\":\"10\",\"offset\":\"0\"}");
        JsonNode output = jsonMapper.readTree(result);

        assertThat(output.path("ankenTotalCount").asText()).isEqualTo("2");
        assertThat(output.path("ankenList")).hasSize(1);
        assertThat(output.path("ankenList").get(0).path("ankenId").asText()).isEqualTo("1000001");
        assertThat(output.path("ankenList").get(0).path("workPlace").asText()).isEqualTo("東京");
        assertThat(output.path("ankenList").get(0).path("languages").asText()).isEqualTo("Java");
        assertThat(output.path("attrGroupList")).hasSize(1);
        assertThat(output.path("attrGroupList").get(0).path("attrGrpName").asText()).isEqualTo("言語");
        assertThat(output.path("attrGroupList").get(0).path("attrs").get(0).path("checked").asBoolean()).isFalse();
    }

    @Test
    void 選択済み属性はattrGroupListでchecked状態として返却されること() {

        stubRecordQuery(0L, List.of(), List.of());

        String result = getAnkenListService.execute("{\"attr1000001\":\"1\"}");
        JsonNode output = jsonMapper.readTree(result);

        assertThat(output.path("attrGroupList").get(0).path("attrs").get(0).path("checked").asBoolean()).isTrue();
        assertThat(output.path("ankenList")).isEmpty();
        assertThat(output.path("ankenTotalCount").asText()).isEqualTo("0");
    }

    /**
     * SQL文の特徴的な部分文字列で処理内容を振り分ける、単一のスタブ応答を登録する。
     */
    private void stubRecordQuery(long ankenTotalCount, List<LinkedHashMap<String, String>> ankenRows,
            List<LinkedHashMap<String, String>> languageRows) {

        LinkedHashMap<String, String> attrGrpRow = new LinkedHashMap<>();
        attrGrpRow.put("ATTR_GRP_ID", "1000001");
        attrGrpRow.put("ATTR_GRP_NAME", "言語");

        LinkedHashMap<String, String> attrRow = new LinkedHashMap<>();
        attrRow.put("ATTR_ID", "1000001");
        attrRow.put("ATTR_NAME", "Java");
        attrRow.put("ATTR_GRP_ID", "1000001");

        LinkedHashMap<String, String> countRow = new LinkedHashMap<>();
        countRow.put("CNT", String.valueOf(ankenTotalCount));

        when(recordQueryService.select(anyString(), anyList())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM ATTR_GRP")) {
                return new ArrayList<>(List.of(attrGrpRow));
            }
            if (sql.contains("SELECT ATTR_ID, ATTR_NAME, ATTR_GRP_ID")) {
                return new ArrayList<>(List.of(attrRow));
            }
            if (sql.contains("COUNT(*)")) {
                return new ArrayList<>(List.of(countRow));
            }
            if (sql.contains("INNER JOIN ATTR T")) {
                return new ArrayList<>(languageRows);
            }
            if (sql.contains("SELECT A.ANKEN_ID")) {
                return new ArrayList<>(ankenRows);
            }
            throw new IllegalArgumentException("未想定のSQL: " + sql);
        });
    }

    private LinkedHashMap<String, String> ankenRow(String ankenId, String workPlace, String ankenName,
            String description, String billingRate, String term) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ANKEN_ID", ankenId);
        row.put("WORK_PLACE", workPlace);
        row.put("ANKEN_NAME", ankenName);
        row.put("DESCRIPTION", description);
        row.put("BILLING_RATE", billingRate);
        row.put("TERM", term);
        return row;
    }

    private LinkedHashMap<String, String> languageRow(String ankenId, String attrName) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ANKEN_ID", ankenId);
        row.put("ATTR_NAME", attrName);
        return row;
    }
}
