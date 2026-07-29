package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;

class TsvTableFileReaderTest {

    private final TsvTableFileReader tsvTableFileReader = new TsvTableFileReader();

    @Test
    void TSVの内容をヘッダの記述順を維持したレコードのリストとして読み込めること() {

        InputStream inputStream = toInputStream("ID\tNAME\n1\tテスト\n2\tテスト2\n");

        ArrayList<LinkedHashMap<String, String>> records = tsvTableFileReader.read(inputStream);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsExactly(java.util.Map.entry("ID", "1"), java.util.Map.entry("NAME", "テスト"));
        assertThat(records.get(1)).containsExactly(java.util.Map.entry("ID", "2"),
                java.util.Map.entry("NAME", "テスト2"));
    }

    @Test
    void 空行はスキップされること() {

        InputStream inputStream = toInputStream("ID\tNAME\n1\tテスト\n\n2\tテスト2\n");

        ArrayList<LinkedHashMap<String, String>> records = tsvTableFileReader.read(inputStream);

        assertThat(records).hasSize(2);
    }

    @Test
    void 値が列数より少ない行は不足分を空文字列として扱うこと() {

        InputStream inputStream = toInputStream("ID\tNAME\n1\n");

        ArrayList<LinkedHashMap<String, String>> records = tsvTableFileReader.read(inputStream);

        assertThat(records.get(0)).containsExactly(java.util.Map.entry("ID", "1"), java.util.Map.entry("NAME", ""));
    }

    @Test
    void ヘッダ行が存在しない場合は例外がスローされること() {

        InputStream inputStream = toInputStream("");

        assertThatThrownBy(() -> tsvTableFileReader.read(inputStream))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ファイルが存在しない場合は例外がスローされること(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {

        java.nio.file.Path notExistFile = tempDir.resolve("NOT_EXIST.txt");

        assertThatThrownBy(() -> tsvTableFileReader.read(notExistFile))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ファイルシステム上のパスからも読み込めること(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {

        java.nio.file.Path dataFile = tempDir.resolve("SAMPLE.txt");
        java.nio.file.Files.writeString(dataFile, "ID\tNAME\n1\tテスト\n", StandardCharsets.UTF_8);

        ArrayList<LinkedHashMap<String, String>> records = tsvTableFileReader.read(dataFile);

        assertThat(records).hasSize(1);
    }

    @Test
    void マーカー文字列はCRとLFとタブへ復元されること() {

        InputStream inputStream = toInputStream("ID\tNAME\n1\ta#Yr#b#Yn#c#Yt#d\n");

        ArrayList<LinkedHashMap<String, String>> records = tsvTableFileReader.read(inputStream);

        assertThat(records.get(0)).containsExactly(java.util.Map.entry("ID", "1"),
                java.util.Map.entry("NAME", "a\rb\nc\td"));
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
