package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import com.freedom.taskall_v2.common.db.DropTableSqlBuilder;
import com.freedom.taskall_v2.common.db.SelectSqlBuilder;
import com.freedom.taskall_v2.common.db.TableDefLoader;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UpdateSqlByDefServiceTest {

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private TableDefLoader tableDefLoader;

    @Mock
    private DropTableSqlBuilder dropTableSqlBuilder;

    @Mock
    private CreateTableSqlBuilder createTableSqlBuilder;

    @Mock
    private SelectSqlBuilder selectSqlBuilder;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private UpdateSqlByDefService updateSqlByDefService;

    @BeforeEach
    void setUp() {
        updateSqlByDefService = new UpdateSqlByDefService(dbMngProperties, tableDefLoader, dropTableSqlBuilder,
                createTableSqlBuilder, selectSqlBuilder, objectMapper, new MsgUtil());
    }

    @Test
    void TBL_DEFファイルからDROP_CREATE_SELECT文を生成しテーブル順を引き継いで出力できること(
            @TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        ArrayList<LinkedHashMap<String, String>> accntDefs = new ArrayList<>(List.of(buildColumnDef("ACCNT")));
        ArrayList<LinkedHashMap<String, String>> aproleDefs = new ArrayList<>(List.of(buildColumnDef("APROLE")));
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = new LinkedHashMap<>();
        tableDefMap.put("ACCNT", accntDefs);
        tableDefMap.put("APROLE", aproleDefs);

        Path tblDefFilePath = tempDir.resolve("data").resolve("TBL_DEF.txt");
        when(tableDefLoader.load(eq(tblDefFilePath))).thenReturn(tableDefMap);
        when(dropTableSqlBuilder.build("ACCNT")).thenReturn("DROP ACCNT;");
        when(dropTableSqlBuilder.build("APROLE")).thenReturn("DROP APROLE;");
        when(createTableSqlBuilder.build(eq("ACCNT"), eq(new ArrayList<Map<String, String>>(accntDefs))))
                .thenReturn("CREATE ACCNT;");
        when(createTableSqlBuilder.build(eq("APROLE"), eq(new ArrayList<Map<String, String>>(aproleDefs))))
                .thenReturn("CREATE APROLE;");
        when(selectSqlBuilder.build(eq("ACCNT"), eq(new ArrayList<Map<String, String>>(accntDefs))))
                .thenReturn("SELECT ACCNT;");
        when(selectSqlBuilder.build(eq("APROLE"), eq(new ArrayList<Map<String, String>>(aproleDefs))))
                .thenReturn("SELECT APROLE;");

        String result = updateSqlByDefService.execute("{}");

        Path sqlDir = tempDir.resolve("sql");
        assertThat(Files.readString(sqlDir.resolve("DROP_ACCNT.sql"), StandardCharsets.UTF_8))
                .isEqualTo("DROP ACCNT;" + System.lineSeparator());
        assertThat(Files.readString(sqlDir.resolve("CREATE_ACCNT.sql"), StandardCharsets.UTF_8))
                .isEqualTo("CREATE ACCNT;" + System.lineSeparator());
        assertThat(Files.readString(sqlDir.resolve("SELECT_ACCNT.sql"), StandardCharsets.UTF_8))
                .isEqualTo("SELECT ACCNT;" + System.lineSeparator());
        assertThat(Files.readString(sqlDir.resolve("DROP_APROLE.sql"), StandardCharsets.UTF_8))
                .isEqualTo("DROP APROLE;" + System.lineSeparator());

        verify(tableDefLoader).load(eq(tblDefFilePath));

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("tableNameList")).hasSize(2);
        assertThat(resultNode.path("tableNameList").get(0).asText()).isEqualTo("ACCNT");
        assertThat(resultNode.path("tableNameList").get(1).asText()).isEqualTo("APROLE");
    }

    private LinkedHashMap<String, String> buildColumnDef(String tableName) {
        LinkedHashMap<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("TABLE_NAME", tableName);
        columnDef.put("FIELD_NAME", tableName + "_ID");
        return columnDef;
    }
}
