package com.freedom.taskall_v2.web.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 案件一覧画面の表示用データ(募集中の公開案件一覧、属性グループ・属性のチェックボックス一覧)を
 * 取得するサービスです。
 *
 * <p>
 * GET(初回表示)・POST(属性検索)いずれからも同一クラスが呼び出され、絞り込み条件の有無以外の
 * 処理内容は変わりません。属性検索は、リクエストパラメータ{@code attr<ATTR_ID>}が{@code "1"}の
 * ものだけをチェック済みとみなし、同一属性グループ内はOR・グループ間はANDで絞り込みます。
 * </p>
 */
@Service
public class GetAnkenListService implements ScriptElementService {

    /** 「公開属性」グループのID。検索パネルには表示せず、常に「公開」案件のみを対象とするために使う */
    private static final String PUBLIC_ATTR_GRP_ID = "1000801";

    /** 「公開」属性のID。この属性を持つ案件のみ一覧表示の対象とする */
    private static final String PUBLIC_ATTR_ID = "1000801";

    /** 「言語」属性グループのID。一覧表の「言語」列に表示する属性を絞り込むために使う */
    private static final String LANGUAGE_ATTR_GRP_ID = "1000001";

    /** ステータス「募集中」を表す値 */
    private static final String STATUS_RECRUITING = "1";

    private static final String ATTR_GRP_SQL = """
            SELECT ATTR_GRP_ID, ATTR_GRP_NAME
            FROM ATTR_GRP
            WHERE ATTR_GRP_ID <> ?
            ORDER BY ATTR_GRP_ID
            """;

    private static final String ATTR_SQL = """
            SELECT ATTR_ID, ATTR_NAME, ATTR_GRP_ID
            FROM ATTR
            WHERE ATTR_GRP_ID <> ?
            ORDER BY ATTR_GRP_ID, ORD_IN_GRP
            """;

    private static final String LANGUAGE_SQL = """
            SELECT X.ANKEN_ID, T.ATTR_NAME
            FROM ATTR_IN_ANKEN X
            INNER JOIN ATTR T ON X.ATTR_ID = T.ATTR_ID
            WHERE T.ATTR_GRP_ID = ? AND X.ANKEN_ID IN (%s)
            ORDER BY X.ANKEN_ID, T.ORD_IN_GRP
            """;

    private final RecordQueryService recordQueryService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public GetAnkenListService(RecordQueryService recordQueryService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);
        int limit = parsePositiveIntOrDefault(context.path("limit").asString(""), 10);
        int offset = parsePositiveIntOrDefault(context.path("offset").asString(""), 0);

        // 属性グループ・属性の一覧(公開属性グループは除く)を取得し、
        // リクエストパラメータのチェック有無を突き合わせて検索パネル表示用データを組み立てる
        List<LinkedHashMap<String, String>> attrGrpRows =
                recordQueryService.select(ATTR_GRP_SQL, List.of(PUBLIC_ATTR_GRP_ID));
        List<LinkedHashMap<String, String>> attrRows = recordQueryService.select(ATTR_SQL, List.of(PUBLIC_ATTR_GRP_ID));

        Map<String, List<String>> selectedAttrIdsByGrp = collectSelectedAttrIdsByGroup(context, attrRows);

        ObjectNode output = objectMapper.createObjectNode();
        output.set("attrGroupList", buildAttrGroupList(attrGrpRows, attrRows, selectedAttrIdsByGrp));

        // 絞り込み条件(公開かつ募集中、選択された属性グループごとのAND条件)を組み立てて対象案件を検索する
        StringBuilder whereClause = new StringBuilder();
        List<String> params = new ArrayList<>();
        buildWhereClause(whereClause, params, selectedAttrIdsByGrp);

        long totalCount = countAnken(whereClause.toString(), params);
        List<LinkedHashMap<String, String>> ankenRows = selectAnkenPage(whereClause.toString(), params, limit, offset);

        output.set("ankenList", buildAnkenList(ankenRows));
        output.put("ankenTotalCount", String.valueOf(totalCount));

        return writeAsString(output);
    }

    private Map<String, List<String>> collectSelectedAttrIdsByGroup(ObjectNode context,
            List<LinkedHashMap<String, String>> attrRows) {

        // attr<ATTR_ID>パラメータが"1"のものだけをチェック済みとみなし、属性グループIDごとに集約する
        Map<String, List<String>> selectedAttrIdsByGrp = new LinkedHashMap<>();
        for (LinkedHashMap<String, String> attrRow : attrRows) {
            String attrId = attrRow.get("ATTR_ID");
            if (!"1".equals(context.path("attr" + attrId).asString(""))) {
                continue;
            }
            String attrGrpId = attrRow.get("ATTR_GRP_ID");
            selectedAttrIdsByGrp.computeIfAbsent(attrGrpId, key -> new ArrayList<>()).add(attrId);
        }
        return selectedAttrIdsByGrp;
    }

    private ArrayNode buildAttrGroupList(List<LinkedHashMap<String, String>> attrGrpRows,
            List<LinkedHashMap<String, String>> attrRows, Map<String, List<String>> selectedAttrIdsByGrp) {

        ArrayNode attrGroupList = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> attrGrpRow : attrGrpRows) {

            String attrGrpId = attrGrpRow.get("ATTR_GRP_ID");
            Set<String> checkedAttrIds = new LinkedHashSet<>(
                    selectedAttrIdsByGrp.getOrDefault(attrGrpId, List.of()));

            ArrayNode attrs = objectMapper.createArrayNode();
            for (LinkedHashMap<String, String> attrRow : attrRows) {
                if (!attrGrpId.equals(attrRow.get("ATTR_GRP_ID"))) {
                    continue;
                }
                ObjectNode attr = objectMapper.createObjectNode();
                attr.put("attrId", attrRow.get("ATTR_ID"));
                attr.put("attrName", attrRow.get("ATTR_NAME"));
                attr.put("checked", checkedAttrIds.contains(attrRow.get("ATTR_ID")));
                attrs.add(attr);
            }

            ObjectNode attrGrp = objectMapper.createObjectNode();
            attrGrp.put("attrGrpId", attrGrpId);
            attrGrp.put("attrGrpName", attrGrpRow.get("ATTR_GRP_NAME"));
            attrGrp.set("attrs", attrs);
            attrGroupList.add(attrGrp);
        }
        return attrGroupList;
    }

    private void buildWhereClause(StringBuilder whereClause, List<String> params,
            Map<String, List<String>> selectedAttrIdsByGrp) {

        // 「募集中」かつ「公開」属性を持つ案件のみを対象とする
        whereClause.append("A.STATUS = ? AND EXISTS (SELECT 1 FROM ATTR_IN_ANKEN X ")
                .append("WHERE X.ANKEN_ID = A.ANKEN_ID AND X.ATTR_ID = ?)");
        params.add(STATUS_RECRUITING);
        params.add(PUBLIC_ATTR_ID);

        // 属性グループ内はOR、グループ間はANDとなるよう、選択されたグループごとにIN句を追加する
        for (List<String> attrIds : selectedAttrIdsByGrp.values()) {
            if (attrIds.isEmpty()) {
                continue;
            }
            whereClause.append(" AND A.ANKEN_ID IN (SELECT ANKEN_ID FROM ATTR_IN_ANKEN WHERE ATTR_ID IN (")
                    .append(placeholders(attrIds.size())).append("))");
            params.addAll(attrIds);
        }
    }

    private long countAnken(String whereClause, List<String> params) {
        String sql = "SELECT COUNT(*) AS CNT FROM ANKEN A WHERE " + whereClause;
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(sql, params);
        return Long.parseLong(rows.get(0).get("CNT"));
    }

    private List<LinkedHashMap<String, String>> selectAnkenPage(String whereClause, List<String> params, int limit,
            int offset) {
        String sql = "SELECT A.ANKEN_ID, A.WORK_PLACE, A.ANKEN_NAME, A.DESCRIPTION, A.BILLING_RATE, A.TERM "
                + "FROM ANKEN A WHERE " + whereClause + " ORDER BY A.UPDATED_AT DESC LIMIT ? OFFSET ?";
        List<String> pageParams = new ArrayList<>(params);
        pageParams.add(String.valueOf(limit));
        pageParams.add(String.valueOf(offset));
        return recordQueryService.select(sql, pageParams);
    }

    private ArrayNode buildAnkenList(List<LinkedHashMap<String, String>> ankenRows) {

        Map<String, String> languagesByAnkenId = findLanguagesByAnkenId(
                ankenRows.stream().map(row -> row.get("ANKEN_ID")).toList());

        ArrayNode ankenList = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> row : ankenRows) {
            ObjectNode anken = objectMapper.createObjectNode();
            String ankenId = row.get("ANKEN_ID");
            anken.put("ankenId", ankenId);
            anken.put("workPlace", row.get("WORK_PLACE"));
            anken.put("languages", languagesByAnkenId.getOrDefault(ankenId, ""));
            anken.put("ankenName", row.get("ANKEN_NAME"));
            anken.put("description", row.get("DESCRIPTION"));
            anken.put("billingRate", row.get("BILLING_RATE"));
            anken.put("term", row.get("TERM"));
            ankenList.add(anken);
        }
        return ankenList;
    }

    private Map<String, String> findLanguagesByAnkenId(List<String> ankenIds) {

        if (ankenIds.isEmpty()) {
            return Map.of();
        }

        String sql = String.format(LANGUAGE_SQL, placeholders(ankenIds.size()));
        List<String> params = new ArrayList<>();
        params.add(LANGUAGE_ATTR_GRP_ID);
        params.addAll(ankenIds);

        // 1案件に複数言語が紐づく場合があるため、案件IDごとに「/」区切りへ集約する
        Map<String, List<String>> languageNamesByAnkenId = new LinkedHashMap<>();
        for (LinkedHashMap<String, String> row : recordQueryService.select(sql, params)) {
            languageNamesByAnkenId.computeIfAbsent(row.get("ANKEN_ID"), key -> new ArrayList<>())
                    .add(row.get("ATTR_NAME"));
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : languageNamesByAnkenId.entrySet()) {
            result.put(entry.getKey(), String.join("/", entry.getValue()));
        }
        return result;
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private int parsePositiveIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? defaultValue : parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
