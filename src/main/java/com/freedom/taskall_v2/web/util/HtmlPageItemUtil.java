package com.freedom.taskall_v2.web.util;

import java.util.List;
import java.util.Map;

/**
 * {@code htmlPage}配列内の画面表示項目({@code items})を、{@code itemKey}を指定して
 * パートを横断して検索するユーティリティです。
 *
 * <p>
 * {@code CreateHtmlService}が出力する{@code htmlPage}構造は、画面表示項目を
 * {@code PARTS_IN_PAGE_ID}(パート)単位でネストします。しかし、あるパート(例:
 * ヘッダー、{@code HTML_PARTS_ID=1000001})の描画に必要な項目(例: {@code urlLink})が、
 * DB上は別のパート({@code HTML_PARTS_ID=1000002})にネストされている場合があるため、
 * Thymeleafテンプレート側で{@code itemKey}を指定してパートを横断的に検索できるようにします。
 * </p>
 */
public final class HtmlPageItemUtil {

    private HtmlPageItemUtil() {
    }

    /**
     * {@code htmlPage}配列全体から、指定した{@code itemKey}を持つ画面表示項目のレコードを検索します。
     *
     * @param htmlPage {@code CreateHtmlService}が出力した{@code htmlPage}配列(Map化済み)
     * @param itemKey  検索対象の項目キー({@code PARTS_ITEM.ITEM_KEY})
     * @return 該当する項目のレコード一覧。見つからない場合は空リスト
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> findRecords(List<Map<String, Object>> htmlPage, String itemKey) {

        // 各パートを順に確認し、items配列を持つパートだけを画面表示項目の検索対象にする。
        for (Map<String, Object> part : htmlPage) {

            Object itemsObj = part.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                continue;
            }

            // パート内の各項目を調べ、指定したitemKeyに一致した時点で対応するrecordsを返却する。
            for (Object itemObj : items) {
                Map<String, Object> item = (Map<String, Object>) itemObj;
                if (itemKey.equals(item.get("itemKey"))) {
                    return (List<Map<String, Object>>) item.get("records");
                }
            }
        }

        return List.of();
    }
}
