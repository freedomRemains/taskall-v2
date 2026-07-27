package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.db.TsvTableFileWriter;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableDataServiceTest {

    @Mock
    private LiveTableColumnDefLoader liveTableColumnDefLoader;

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private TsvTableFileWriter tsvTableFileWriter;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GetAllTableDataService getAllTableDataService;

    @BeforeEach
    void setUp() {
        getAllTableDataService = new GetAllTableDataService(liveTableColumnDefLoader, recordQueryService,
                dbMngProperties, tsvTableFileWriter, objectMapper, new MsgUtil());
    }

    @Test
    void 取得件数が上限未満になるまでページング取得しながらTSVへ追記すること(@TempDir Path tempDir)
            throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());
        when(liveTableColumnDefLoader.load("ACCNT")).thenReturn(List.of(buildColumnDef("ACCNT_ID")));

        ArrayList<LinkedHashMap<String, String>> firstBatch = createRows(GetAllTableDataService.BATCH_SIZE, 0);
        ArrayList<LinkedHashMap<String, String>> secondBatch = createRows(2, GetAllTableDataService.BATCH_SIZE);
        when(recordQueryService.select(eq("SELECT ACCNT_ID FROM ACCNT ORDER BY ACCNT_ID LIMIT 5000 OFFSET 0")))
                .thenReturn(firstBatch);
        when(recordQueryService.select(eq("SELECT ACCNT_ID FROM ACCNT ORDER BY ACCNT_ID LIMIT 5000 OFFSET 5000")))
                .thenReturn(secondBatch);

        String result = getAllTableDataService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        Path dataFilePath = tempDir.resolve("data").resolve("ACCNT.txt");
        verify(tsvTableFileWriter).append(eq(dataFilePath), eq(firstBatch));
        verify(tsvTableFileWriter).append(eq(dataFilePath), eq(secondBatch));

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("dbDataDirPath").asText()).isEqualTo(tempDir.resolve("data").toString());
    }

    @Test
    void 最初のページング取得結果が空の場合はTSVへ追記しないこと(@TempDir Path tempDir) {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());
        when(liveTableColumnDefLoader.load("ACCNT")).thenReturn(List.of(buildColumnDef("ACCNT_ID")));
        when(recordQueryService.select(eq("SELECT ACCNT_ID FROM ACCNT ORDER BY ACCNT_ID LIMIT 5000 OFFSET 0")))
                .thenReturn(new ArrayList<>());

        getAllTableDataService.execute("{\"tableNameList\":[\"ACCNT\"]}");

        verify(tsvTableFileWriter, never()).append(eq(tempDir.resolve("data").resolve("ACCNT.txt")),
                org.mockito.ArgumentMatchers.anyList());
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
