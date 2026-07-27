package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetTableNameListServiceTest {

    private static final String TABLE_NAME_SQL = """
            SELECT DISTINCT TABLE_NAME
            FROM TBL_DEF
            ORDER BY TABLE_NAME
            """;

    @Mock
    private RecordQueryService recordQueryService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GetTableNameListService getTableNameListService;

    @BeforeEach
    void setUp() {
        getTableNameListService = new GetTableNameListService(recordQueryService, objectMapper, new MsgUtil());
    }

    @Test
    void ライブDBから取得したテーブル名一覧をtableNameListとして出力できること() throws Exception {

        LinkedHashMap<String, String> accntRow = new LinkedHashMap<>();
        accntRow.put("TABLE_NAME", "ACCNT");
        LinkedHashMap<String, String> aproleRow = new LinkedHashMap<>();
        aproleRow.put("TABLE_NAME", "APROLE");

        when(recordQueryService.select(eq(TABLE_NAME_SQL)))
                .thenReturn(new ArrayList<>(List.of(accntRow, aproleRow)));

        String result = getTableNameListService.execute("{}");

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("tableNameList")).hasSize(2);
        assertThat(resultNode.path("tableNameList").get(0).asText()).isEqualTo("ACCNT");
        assertThat(resultNode.path("tableNameList").get(1).asText()).isEqualTo("APROLE");
    }
}
