package com.freedom.taskall_v2.common.service.dbmng;

import java.util.ArrayList;
import java.util.List;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DBメンテナンス機能(DB構成取得/DB構成更新)のスクリプト要素サービス群で共通利用する、
 * JSON入出力の補助処理をまとめたユーティリティクラスです。
 *
 * <p>
 * 本パッケージの各サービスは、{@code GetTableNameListService}が設定した{@code tableNameList}
 * を繰り返し参照するため、コンテキストJSONの読み書きと合わせてここへ集約しています。
 * </p>
 */
final class DbMngJsonUtil {

    private DbMngJsonUtil() {
    }

    static ObjectNode readAsObjectNode(ObjectMapper objectMapper, MsgUtil msg, String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    static String writeAsString(ObjectMapper objectMapper, MsgUtil msg, ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }

    /**
     * コンテキストに設定済みの{@code tableNameList}(前段の{@code GetTableNameListService}が
     * 設定したテーブル名一覧)を、リスト形式で取り出します。
     */
    static List<String> readTableNameList(ObjectNode context) {
        List<String> tableNameList = new ArrayList<>();
        for (JsonNode tableNameNode : context.path("tableNameList")) {
            tableNameList.add(tableNameNode.asString());
        }
        return tableNameList;
    }
}
