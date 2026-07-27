package com.freedom.taskall_v2.common.service.dbmng;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ライブDB(実行時のTBL_DEFテーブル)から、対象テーブル名の一覧を取得するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetTableNameListService}に相当します。DB構成取得(バックアップ)
 * スクリプトの先頭で実行され、後続の各サービス(定義/SQL/データ生成)が同じテーブル一覧を
 * 使い回せるよう、{@code tableNameList}としてコンテキストへ設定します。
 * </p>
 */
@Service
public class GetTableNameListService implements ScriptElementService {

    private static final String TABLE_NAME_SQL = """
            SELECT DISTINCT TABLE_NAME
            FROM TBL_DEF
            ORDER BY TABLE_NAME
            """;

    private final RecordQueryService recordQueryService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public GetTableNameListService(RecordQueryService recordQueryService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // ライブDBのTBL_DEFから、現時点でDBに存在するテーブル名を重複なく取得する
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(TABLE_NAME_SQL);

        ArrayNode tableNameList = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> row : rows) {
            tableNameList.add(row.get("TABLE_NAME"));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.set("tableNameList", tableNameList);
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }
}
