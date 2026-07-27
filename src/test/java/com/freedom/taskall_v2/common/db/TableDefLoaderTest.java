package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class TableDefLoaderTest {

    private final TableDefLoader tableDefLoader = new TableDefLoader();

    @Test
    void TABLE_NAMEカラムの値ごとにグルーピングされ出現順を維持すること() {

        String content = "TABLE_NAME\tFIELD_NAME\n"
                + "TBL_B\tID\n"
                + "TBL_A\tID\n"
                + "TBL_B\tNAME\n";

        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = tableDefLoader
                .load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertThat(tableDefMap.keySet()).containsExactly("TBL_B", "TBL_A");
        assertThat(tableDefMap.get("TBL_B")).hasSize(2);
        assertThat(tableDefMap.get("TBL_A")).hasSize(1);
    }

    @Test
    void ファイルシステム上のパスからも読み込めること(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {

        java.nio.file.Path tblDefFile = tempDir.resolve("TBL_DEF.txt");
        java.nio.file.Files.writeString(tblDefFile, "TABLE_NAME\tFIELD_NAME\nTBL_A\tID\n", StandardCharsets.UTF_8);

        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = tableDefLoader
                .load(tblDefFile);

        assertThat(tableDefMap.keySet()).containsExactly("TBL_A");
    }
}
