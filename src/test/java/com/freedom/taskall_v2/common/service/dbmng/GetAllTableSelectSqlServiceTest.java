package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.SelectSqlBuilder;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableSelectSqlServiceTest {

    @Mock
    private LiveTableColumnDefLoader liveTableColumnDefLoader;

    @Mock
    private DbMngProperties dbMngProperties;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final MsgUtil msg = new MsgUtil();

    private GetAllTableSelectSqlService getAllTableSelectSqlService;

    @BeforeEach
    void setUp() {
        getAllTableSelectSqlService = new GetAllTableSelectSqlService(liveTableColumnDefLoader, dbMngProperties,
                new SelectSqlBuilder(msg), objectMapper, msg);
    }

    @Test
    void ライブDBのカラム定義からSELECT文を生成して書き出せること(@TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ACCNT_ID", "PRI"),
                buildColumnDef("ACCOUNT_NAME", null));
        when(liveTableColumnDefLoader.load("ACCNT")).thenReturn(columnDefs);

        String result = getAllTableSelectSqlService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        Path sqlFilePath = tempDir.resolve("sql").resolve("SELECT_ACCNT.sql");
        assertThat(Files.readString(sqlFilePath, StandardCharsets.UTF_8))
                .isEqualTo("SELECT ACCNT_ID, ACCOUNT_NAME FROM ACCNT ORDER BY ACCNT_ID;"
                        + System.lineSeparator());

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("selectSqlDirPath").asText()).isEqualTo(tempDir.resolve("sql").toString());
    }

    private Map<String, String> buildColumnDef(String fieldName, String keyDiv) {
        Map<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        if (keyDiv != null) {
            columnDef.put("KEY_DIV", keyDiv);
        }
        return columnDef;
    }
}
