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
import com.freedom.taskall_v2.common.db.CreateTableSqlBuilder;
import com.freedom.taskall_v2.common.db.sqlite.SqlitePrimaryKeyColumnSqlBuilder;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableCreateSqlServiceTest {

    @Mock
    private LiveTableColumnDefLoader liveTableColumnDefLoader;

    @Mock
    private DbMngProperties dbMngProperties;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final MsgUtil msg = new MsgUtil();

    private GetAllTableCreateSqlService getAllTableCreateSqlService;

    @BeforeEach
    void setUp() {
        getAllTableCreateSqlService = new GetAllTableCreateSqlService(liveTableColumnDefLoader, dbMngProperties,
                new CreateTableSqlBuilder(msg, new SqlitePrimaryKeyColumnSqlBuilder()), objectMapper, msg);
    }

    @Test
    void ライブDBのカラム定義からCREATE_TABLE文を生成して書き出せること(@TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        List<Map<String, String>> columnDefs = List.of(
                buildColumnDef("ACCNT_ID", "INT", "NO", "PRI", "null"),
                buildColumnDef("ACCOUNT_NAME", "TEXT", "NO", "", "null"));
        when(liveTableColumnDefLoader.load("ACCNT")).thenReturn(columnDefs);

        String result = getAllTableCreateSqlService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        String expectedSql = new CreateTableSqlBuilder(msg, new SqlitePrimaryKeyColumnSqlBuilder())
                .build("ACCNT", columnDefs);
        Path sqlFilePath = tempDir.resolve("sql").resolve("CREATE_ACCNT.sql");
        assertThat(Files.readString(sqlFilePath, StandardCharsets.UTF_8))
                .isEqualTo(expectedSql + System.lineSeparator());

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("createSqlDirPath").asText()).isEqualTo(tempDir.resolve("sql").toString());
    }

    private Map<String, String> buildColumnDef(String fieldName, String typeName, String allowNull, String keyDiv,
            String defaultValue) {
        Map<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        columnDef.put("TYPE_NAME", typeName);
        columnDef.put("ALLOW_NULL", allowNull);
        columnDef.put("KEY_DIV", keyDiv);
        columnDef.put("DEFAULT_VALUE", defaultValue);
        return columnDef;
    }
}
