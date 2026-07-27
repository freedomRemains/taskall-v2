package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.InsertSqlBuilder;
import com.freedom.taskall_v2.common.db.TableDefLoader;
import com.freedom.taskall_v2.common.db.TsvTableFileReader;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UpdateSqlByDataServiceTest {

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private TableDefLoader tableDefLoader;

    @Mock
    private TsvTableFileReader tsvTableFileReader;

    @Mock
    private InsertSqlBuilder insertSqlBuilder;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private UpdateSqlByDataService updateSqlByDataService;

    @BeforeEach
    void setUp() {
        updateSqlByDataService = new UpdateSqlByDataService(dbMngProperties, tableDefLoader, tsvTableFileReader,
                insertSqlBuilder, objectMapper, new MsgUtil());
    }

    @Test
    void データファイルが存在するテーブルだけINSERT_SQLを生成できること(@TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        Path workDir = tempDir;
        Path accntDataFilePath = workDir.resolve("data").resolve("ACCNT.txt");
        Files.createDirectories(accntDataFilePath.getParent());
        Files.writeString(accntDataFilePath, "ACCNT_ID\n1\n", StandardCharsets.UTF_8);

        ArrayList<LinkedHashMap<String, String>> accntDefs = new ArrayList<>(List.of(buildColumnDef("ACCNT_ID")));
        ArrayList<LinkedHashMap<String, String>> aproleDefs = new ArrayList<>(List.of(buildColumnDef("APROLE_ID")));
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = new LinkedHashMap<>();
        tableDefMap.put("ACCNT", accntDefs);
        tableDefMap.put("APROLE", aproleDefs);
        when(tableDefLoader.load(eq(workDir.resolve("data").resolve("TBL_DEF.txt")))).thenReturn(tableDefMap);

        ArrayList<LinkedHashMap<String, String>> dataRows = new ArrayList<>(List.of(buildDataRow("1")));
        when(tsvTableFileReader.read(eq(accntDataFilePath))).thenReturn(dataRows);
        when(insertSqlBuilder.build(eq("ACCNT"), eq(new ArrayList<Map<String, String>>(accntDefs)), anyList()))
                .thenReturn(List.of("INSERT ACCNT;"));

        String result = updateSqlByDataService.execute("{\"tableNameList\":[\"ACCNT\",\"APROLE\"]}");

        Path sqlDir = workDir.resolve("sql");
        assertThat(Files.readString(sqlDir.resolve("INSERT_ACCNT.sql"), StandardCharsets.UTF_8))
                .isEqualTo("INSERT ACCNT;" + System.lineSeparator());
        assertThat(sqlDir.resolve("INSERT_APROLE.sql")).doesNotExist();
        verify(tsvTableFileReader, never()).read(eq(workDir.resolve("data").resolve("APROLE.txt")));

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("insertSqlDirPath").asText()).isEqualTo(sqlDir.toString());
    }

    @Test
    void データ行は5000件単位で分割してINSERT_SQLへ追記されること(@TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        Path workDir = tempDir;
        Path accntDataFilePath = workDir.resolve("data").resolve("ACCNT.txt");
        Files.createDirectories(accntDataFilePath.getParent());
        Files.writeString(accntDataFilePath, "ACCNT_ID\n1\n", StandardCharsets.UTF_8);

        ArrayList<LinkedHashMap<String, String>> accntDefs = new ArrayList<>(List.of(buildColumnDef("ACCNT_ID")));
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = new LinkedHashMap<>();
        tableDefMap.put("ACCNT", accntDefs);
        when(tableDefLoader.load(eq(workDir.resolve("data").resolve("TBL_DEF.txt")))).thenReturn(tableDefMap);

        ArrayList<LinkedHashMap<String, String>> dataRows = IntStream.range(0, UpdateSqlByDataService.BATCH_SIZE + 1)
                .collect(ArrayList::new,
                        (rows, index) -> rows.add(buildDataRow(String.valueOf(index + 1))),
                        ArrayList::addAll);
        when(tsvTableFileReader.read(eq(accntDataFilePath))).thenReturn(dataRows);
        when(insertSqlBuilder.build(eq("ACCNT"), eq(new ArrayList<Map<String, String>>(accntDefs)), anyList()))
                .thenReturn(List.of("INSERT BATCH1;"), List.of("INSERT BATCH2;"));

        updateSqlByDataService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        Path insertSqlFilePath = workDir.resolve("sql").resolve("INSERT_ACCNT.sql");
        assertThat(Files.readString(insertSqlFilePath, StandardCharsets.UTF_8))
                .isEqualTo("INSERT BATCH1;" + System.lineSeparator()
                        + "INSERT BATCH2;" + System.lineSeparator());
        verify(insertSqlBuilder, times(2)).build(eq("ACCNT"), eq(new ArrayList<Map<String, String>>(accntDefs)),
                anyList());
    }

    private LinkedHashMap<String, String> buildColumnDef(String fieldName) {
        LinkedHashMap<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        return columnDef;
    }

    private LinkedHashMap<String, String> buildDataRow(String id) {
        LinkedHashMap<String, String> dataRow = new LinkedHashMap<>();
        dataRow.put("ACCNT_ID", id);
        return dataRow;
    }
}
