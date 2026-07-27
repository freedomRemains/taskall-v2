package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.SqlFileExecutionService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UpdateAllTableServiceTest {

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private SqlFileExecutionService sqlFileExecutionService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private UpdateAllTableService updateAllTableService;

    @BeforeEach
    void setUp() {
        updateAllTableService = new UpdateAllTableService(dbMngProperties, sqlFileExecutionService, objectMapper,
                new MsgUtil());
    }

    @Test
    void DROP_CREATE_INSERTの順で全テーブル分のSQLファイルが実行されること() throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn("work");

        String result = updateAllTableService.execute("{\"tableNameList\":[\"ACCNT\",\"APROLE\"]}");

        Path sqlDir = Path.of("work").resolve("sql");
        InOrder inOrder = inOrder(sqlFileExecutionService);
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("DROP_ACCNT.sql"));
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("DROP_APROLE.sql"));
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("CREATE_ACCNT.sql"));
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("CREATE_APROLE.sql"));
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("INSERT_ACCNT.sql"));
        inOrder.verify(sqlFileExecutionService).execute(sqlDir.resolve("INSERT_APROLE.sql"));

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("dbUpdateCompleted").asBoolean()).isTrue();
    }
}
