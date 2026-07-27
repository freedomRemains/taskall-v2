package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.DropTableSqlBuilder;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableDropSqlServiceTest {

    @Mock
    private DbMngProperties dbMngProperties;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GetAllTableDropSqlService getAllTableDropSqlService;

    @BeforeEach
    void setUp() {
        getAllTableDropSqlService = new GetAllTableDropSqlService(dbMngProperties, new DropTableSqlBuilder(),
                objectMapper, new MsgUtil());
    }

    @Test
    void 全テーブル分のDROP_TABLE文をSQLファイルへ書き出せること(@TempDir Path tempDir) throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        String result = getAllTableDropSqlService.execute("{\"tableNameList\":[\"ACCNT\",\"APROLE\"]}");

        Path sqlDir = tempDir.resolve("sql");
        assertThat(Files.readString(sqlDir.resolve("DROP_ACCNT.sql"), StandardCharsets.UTF_8))
                .isEqualTo("DROP TABLE IF EXISTS ACCNT;" + System.lineSeparator());
        assertThat(Files.readString(sqlDir.resolve("DROP_APROLE.sql"), StandardCharsets.UTF_8))
                .isEqualTo("DROP TABLE IF EXISTS APROLE;" + System.lineSeparator());

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("dropSqlDirPath").asText()).isEqualTo(sqlDir.toString());
    }
}
