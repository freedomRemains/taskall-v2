package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TsvTableFileWriterTest {

    private final TsvTableFileWriter tsvTableFileWriter = new TsvTableFileWriter();

    @Test
    void writeはヘッダ順を維持したTSVを出力し既存内容を上書きすること(@TempDir Path tempDir) throws Exception {

        Path filePath = tempDir.resolve("ACCNT.txt");
        Files.writeString(filePath, "old", StandardCharsets.UTF_8);

        tsvTableFileWriter.write(filePath, List.of(
                buildRow("ACCNT_ID", "1", "ACCOUNT_NAME", "ゲスト"),
                buildRow("ACCNT_ID", "2", "ACCOUNT_NAME", null)));

        assertThat(Files.readString(filePath, StandardCharsets.UTF_8))
                .isEqualTo("ACCNT_ID\tACCOUNT_NAME" + System.lineSeparator()
                        + "1\tゲスト" + System.lineSeparator()
                        + "2\tnull" + System.lineSeparator());
    }

    @Test
    void appendはファイルが無い場合に新規作成し既存ファイルにはヘッダを重複出力しないこと(@TempDir Path tempDir)
            throws Exception {

        Path filePath = tempDir.resolve("ACCNT.txt");

        tsvTableFileWriter.append(filePath,
                List.of(buildRow("ACCNT_ID", "1", "ACCOUNT_NAME", "ゲスト")));
        tsvTableFileWriter.append(filePath,
                List.of(buildRow("ACCNT_ID", "2", "ACCOUNT_NAME", "管理者")));

        assertThat(Files.readString(filePath, StandardCharsets.UTF_8))
                .isEqualTo("ACCNT_ID\tACCOUNT_NAME" + System.lineSeparator()
                        + "1\tゲスト" + System.lineSeparator()
                        + "2\t管理者" + System.lineSeparator());
    }

    private LinkedHashMap<String, String> buildRow(String key1, String value1, String key2, String value2) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put(key1, value1);
        row.put(key2, value2);
        return row;
    }
}
