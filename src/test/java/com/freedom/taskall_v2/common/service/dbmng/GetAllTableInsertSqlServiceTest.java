package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableInsertSqlServiceTest {

    @Mock
    private LiveTableColumnDefLoader liveTableColumnDefLoader;

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private InsertSqlBuilder insertSqlBuilder;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GetAllTableInsertSqlService getAllTableInsertSqlService;

    @BeforeEach
    void setUp() {
        getAllTableInsertSqlService = new GetAllTableInsertSqlService(liveTableColumnDefLoader, recordQueryService,
                dbMngProperties, insertSqlBuilder, objectMapper, new MsgUtil());
    }

    @Test
    void 取得件数が上限未満になるまでページング取得しながらINSERT_SQLを追記すること(@TempDir Path tempDir)
            throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());
        List<Map<String, String>> columnDefs = List.of(buildColumnDef("ACCNT_ID"));
        when(liveTableColumnDefLoader.load("ACCNT")).thenReturn(columnDefs);

        ArrayList<LinkedHashMap<String, String>> firstBatch = createRows(GetAllTableInsertSqlService.BATCH_SIZE, 0);
        ArrayList<LinkedHashMap<String, String>> secondBatch = createRows(1, GetAllTableInsertSqlService.BATCH_SIZE);
        when(recordQueryService.select(eq("SELECT ACCNT_ID FROM ACCNT ORDER BY ACCNT_ID LIMIT 5000 OFFSET 0")))
                .thenReturn(firstBatch);
        when(recordQueryService.select(eq("SELECT ACCNT_ID FROM ACCNT ORDER BY ACCNT_ID LIMIT 5000 OFFSET 5000")))
                .thenReturn(secondBatch);
        when(insertSqlBuilder.build(eq("ACCNT"), eq(columnDefs), anyList()))
                .thenReturn(List.of("INSERT 1;"), List.of("INSERT 2;"));

        String result = getAllTableInsertSqlService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        Path sqlFilePath = tempDir.resolve("sql").resolve("INSERT_ACCNT.sql");
        assertThat(Files.readString(sqlFilePath, StandardCharsets.UTF_8))
                .isEqualTo("INSERT 1;" + System.lineSeparator() + "INSERT 2;" + System.lineSeparator());
        verify(insertSqlBuilder, times(2)).build(eq("ACCNT"), eq(columnDefs), anyList());

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("insertSqlDirPath").asText()).isEqualTo(tempDir.resolve("sql").toString());
    }

    private Map<String, String> buildColumnDef(String fieldName) {
        Map<String, String> columnDef = new LinkedHashMap<>();
        columnDef.put("FIELD_NAME", fieldName);
        return columnDef;
    }

    private ArrayList<LinkedHashMap<String, String>> createRows(int size, int startId) {
        return IntStream.range(0, size)
                .collect(ArrayList::new, (rows, index) -> rows.add(buildRow(String.valueOf(startId + index + 1))),
                        ArrayList::addAll);
    }

    private LinkedHashMap<String, String> buildRow(String id) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", id);
        return row;
    }
}
