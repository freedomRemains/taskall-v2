package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class SqlFileExecutionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SqlFileExecutionService sqlFileExecutionService;

    @BeforeEach
    void setUp() {
        sqlFileExecutionService = new SqlFileExecutionService(jdbcTemplate, new MsgUtil());
    }

    @Test
    void SQLファイルをセミコロン区切りで分割し5000件単位でバッチ実行できること(@TempDir Path tempDir) throws Exception {

        Path sqlFilePath = tempDir.resolve("INSERT_ACCNT.sql");
        String sqlContent = IntStream.rangeClosed(1, SqlFileExecutionService.BATCH_SIZE + 1)
                .mapToObj(index -> "INSERT INTO ACCNT VALUES (" + index + ");")
                .collect(Collectors.joining(System.lineSeparator()));
        Files.writeString(sqlFilePath, sqlContent, StandardCharsets.UTF_8);

        sqlFileExecutionService.execute(sqlFilePath);

        ArgumentCaptor<String[]> batchCaptor = ArgumentCaptor.forClass(String[].class);
        verify(jdbcTemplate, times(2)).batchUpdate(batchCaptor.capture());
        List<String[]> batches = batchCaptor.getAllValues();
        assertThat(batches.get(0)).hasSize(SqlFileExecutionService.BATCH_SIZE);
        assertThat(batches.get(0)[0]).isEqualTo("INSERT INTO ACCNT VALUES (1);");
        assertThat(batches.get(1)).containsExactly("INSERT INTO ACCNT VALUES (5001);");
    }

    @Test
    void SQLファイルが存在しない場合は何も実行しないこと(@TempDir Path tempDir) {

        sqlFileExecutionService.execute(tempDir.resolve("NOT_EXIST.sql"));

        verify(jdbcTemplate, never()).batchUpdate(org.mockito.ArgumentMatchers.any(String[].class));
    }
}
