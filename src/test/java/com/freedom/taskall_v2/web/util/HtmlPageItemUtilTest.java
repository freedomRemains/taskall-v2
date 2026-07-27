package com.freedom.taskall_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link HtmlPageItemUtil}のテストです。
 */
class HtmlPageItemUtilTest {

    private Map<String, Object> part(String partsInPageId, String htmlPartsId, List<Map<String, Object>> items) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("partsInPageId", partsInPageId);
        part.put("htmlPartsId", htmlPartsId);
        part.put("items", items);
        return part;
    }

    private Map<String, Object> item(String itemKey, List<Map<String, Object>> records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemKey", itemKey);
        item.put("records", records);
        return item;
    }

    private Map<String, Object> record(String key, String value) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(key, value);
        return record;
    }

    @Test
    void 自分自身と異なるpartにネストされたitemKeyのレコードも取得できること() {

        List<Map<String, Object>> urlLinkRecords = List.of(record("URI_PATTERN", "/taskall-v2/service/top.html"));
        Map<String, Object> systemNamePart = part("1000201", "1000001",
                List.of(item("systemName", List.of(record("GNR_VAL", "Taskall")))));
        Map<String, Object> headerPart = part("1000202", "1000002", List.of(item("urlLink", urlLinkRecords)));

        List<Map<String, Object>> htmlPage = List.of(systemNamePart, headerPart);

        List<Map<String, Object>> result = HtmlPageItemUtil.findRecords(htmlPage, "urlLink");

        assertThat(result).isEqualTo(urlLinkRecords);
    }

    @Test
    void 該当するitemKeyが存在しない場合は空リストを返すこと() {

        Map<String, Object> part = part("1000201", "1000001",
                List.of(item("systemName", List.of(record("GNR_VAL", "Taskall")))));

        List<Map<String, Object>> result = HtmlPageItemUtil.findRecords(List.of(part), "linkList");

        assertThat(result).isEmpty();
    }

    @Test
    void itemsが存在しないpartがあっても例外にならず処理を継続できること() {

        Map<String, Object> partWithoutItems = new LinkedHashMap<>();
        partWithoutItems.put("partsInPageId", "1000201");
        partWithoutItems.put("htmlPartsId", "1000001");

        Map<String, Object> partWithItem = part("1000202", "1000002",
                List.of(item("urlLink", List.of(record("URI_PATTERN", "/taskall-v2/service/top.html")))));

        List<Map<String, Object>> result =
                HtmlPageItemUtil.findRecords(List.of(partWithoutItems, partWithItem), "urlLink");

        assertThat(result).hasSize(1);
    }
}
