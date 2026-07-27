package com.freedom.taskall_v2.common.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 「db/data」配下の資材(TBL_DEF.txt、及び各テーブルのデータファイル)から、
 * DROP／CREATE／INSERT／SELECTのSQLファイルを「db/sql」配下に生成するクラスです。
 *
 * <p>
 * 「TBL_DEF」テーブル自身のデータは、定義ファイルである「TBL_DEF.txt」がそのままデータファイルを
 * 兼ねます（ファイル名の命名規則「&lt;テーブル物理名&gt;.txt」に、TBL_DEF.txt自身が一致するため）。
 * </p>
 */
public class DbSchemaSqlGenerator {

    private final TableDefLoader tableDefLoader;
    private final TsvTableFileReader tsvTableFileReader;
    private final DropTableSqlBuilder dropTableSqlBuilder;
    private final CreateTableSqlBuilder createTableSqlBuilder;
    private final InsertSqlBuilder insertSqlBuilder;
    private final SelectSqlBuilder selectSqlBuilder;
    private final MsgUtil msg;

    public DbSchemaSqlGenerator() {
        this(new TableDefLoader(), new TsvTableFileReader(), new DropTableSqlBuilder(),
                new CreateTableSqlBuilder(), new InsertSqlBuilder(), new SelectSqlBuilder(), new MsgUtil());
    }

    public DbSchemaSqlGenerator(TableDefLoader tableDefLoader, TsvTableFileReader tsvTableFileReader,
            DropTableSqlBuilder dropTableSqlBuilder, CreateTableSqlBuilder createTableSqlBuilder,
            InsertSqlBuilder insertSqlBuilder, SelectSqlBuilder selectSqlBuilder, MsgUtil msg) {
        this.tableDefLoader = tableDefLoader;
        this.tsvTableFileReader = tsvTableFileReader;
        this.dropTableSqlBuilder = dropTableSqlBuilder;
        this.createTableSqlBuilder = createTableSqlBuilder;
        this.insertSqlBuilder = insertSqlBuilder;
        this.selectSqlBuilder = selectSqlBuilder;
        this.msg = msg;
    }

    /**
     * DROP／CREATE／INSERT／SELECTのSQLファイルを生成します。
     *
     * @param dataDir 「TBL_DEF.txt」及び各テーブルのデータファイルが配置されたディレクトリ
     * @param sqlDir  生成したSQLファイルの出力先ディレクトリ
     */
    public void generateAll(Path dataDir, Path sqlDir) {

        // TBL_DEFを読み込み、SQL生成対象となる全テーブルの定義を準備する
        Path tblDefFilePath = dataDir.resolve("TBL_DEF.txt");
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap =
                tableDefLoader.load(tblDefFilePath);

        // 出力先ディレクトリを確保したうえで、各テーブル向けのSQLファイルを順次生成する
        createDirectoryIfAbsent(sqlDir);

        for (Map.Entry<String, ArrayList<LinkedHashMap<String, String>>> entry : tableDefMap.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, String>> columnDefs = new ArrayList<>(entry.getValue());

            writeSqlFile(sqlDir.resolve("DROP_" + tableName + ".sql"),
                    dropTableSqlBuilder.build(tableName));
            writeSqlFile(sqlDir.resolve("CREATE_" + tableName + ".sql"),
                    createTableSqlBuilder.build(tableName, columnDefs));
            writeSqlFile(sqlDir.resolve("SELECT_" + tableName + ".sql"),
                    selectSqlBuilder.build(tableName, columnDefs));

            // データファイルが存在するテーブルについてのみ、INSERT文も生成して出力する
            Path dataFilePath = dataDir.resolve(tableName + ".txt");
            if (Files.exists(dataFilePath)) {
                List<Map<String, String>> dataRows = new ArrayList<>(tsvTableFileReader.read(dataFilePath));
                List<String> insertSqlList = insertSqlBuilder.build(tableName, columnDefs, dataRows);
                writeSqlFile(sqlDir.resolve("INSERT_" + tableName + ".sql"), String.join("\n", insertSqlList));
            }
        }
    }

    private void createDirectoryIfAbsent(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.directoryCreateFailed", dir), e);
        }
    }

    private void writeSqlFile(Path filePath, String content) {
        try {
            Files.writeString(filePath, content + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileWriteFailed", filePath), e);
        }
    }
}
