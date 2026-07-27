package com.freedom.taskall_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.InsertSqlBuilder;
import com.freedom.taskall_v2.common.db.TableDefLoader;
import com.freedom.taskall_v2.common.db.TsvTableFileReader;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 作業ディレクトリに配置されたテーブルデータファイル({@code data/<テーブル名>.txt})から、
 * INSERT文を生成し、実行用SQLディレクトリへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code UpdateSqlByDataService}に相当します。DB構成更新(リストア)の
 * 2段階目として、{@link UpdateSqlByDefService}が生成したスキーマ側SQLに続き、データ側の
 * INSERT文を生成します。{@link #BATCH_SIZE}件ずつ区切ってINSERT文を生成・追記することで、
 * SQL生成時点での文字列サイズの肥大化を抑えます。
 * </p>
 */
@Service
public class UpdateSqlByDataService implements ScriptElementService {

    /** 1回のINSERT文生成あたりの最大件数 */
    static final int BATCH_SIZE = 5000;

    private static final String DATA_DIR_NAME = "data";
    private static final String SQL_DIR_NAME = "sql";
    private static final String TBL_DEF_FILE_NAME = "TBL_DEF.txt";

    private final DbMngProperties dbMngProperties;
    private final TableDefLoader tableDefLoader;
    private final TsvTableFileReader tsvTableFileReader;
    private final InsertSqlBuilder insertSqlBuilder;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public UpdateSqlByDataService(DbMngProperties dbMngProperties, ObjectMapper objectMapper, MsgUtil msg) {
        this(dbMngProperties, new TableDefLoader(), new TsvTableFileReader(msg), new InsertSqlBuilder(msg),
                objectMapper, msg);
    }

    public UpdateSqlByDataService(DbMngProperties dbMngProperties, TableDefLoader tableDefLoader,
            TsvTableFileReader tsvTableFileReader, InsertSqlBuilder insertSqlBuilder, ObjectMapper objectMapper,
            MsgUtil msg) {
        this.dbMngProperties = dbMngProperties;
        this.tableDefLoader = tableDefLoader;
        this.tsvTableFileReader = tsvTableFileReader;
        this.insertSqlBuilder = insertSqlBuilder;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path workDir = Path.of(dbMngProperties.getWorkDir());
        Map<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap =
                tableDefLoader.load(workDir.resolve(DATA_DIR_NAME).resolve(TBL_DEF_FILE_NAME));

        Path sqlDir = workDir.resolve(SQL_DIR_NAME);
        createDirectoryIfAbsent(sqlDir);

        for (String tableName : tableNameList) {
            writeInsertSql(workDir, sqlDir, tableName, tableDefMap.get(tableName));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("insertSqlDirPath", sqlDir.toString());
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }

    private void writeInsertSql(Path workDir, Path sqlDir, String tableName,
            List<LinkedHashMap<String, String>> columnDefRows) {

        Path dataFilePath = workDir.resolve(DATA_DIR_NAME).resolve(tableName + ".txt");
        if (columnDefRows == null || !Files.exists(dataFilePath)) {
            // データファイルが存在しないテーブル(データ0件など)は、INSERT文の生成対象外とする
            return;
        }

        List<Map<String, String>> columnDefs = new ArrayList<>(columnDefRows);
        List<Map<String, String>> dataRows = new ArrayList<>(tsvTableFileReader.read(dataFilePath));
        Path insertSqlFilePath = sqlDir.resolve("INSERT_" + tableName + ".sql");

        // 前回実行時のファイルが残っていると内容が重複して追記されてしまうため、先に削除しておく
        deleteIfExists(insertSqlFilePath);

        // BATCH_SIZE件ずつ区切ってINSERT文を生成し、都度ファイルへ追記する
        for (int fromIndex = 0; fromIndex < dataRows.size(); fromIndex += BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_SIZE, dataRows.size());
            List<Map<String, String>> batch = dataRows.subList(fromIndex, toIndex);

            // ライブDBの値はseedデータのように事前エスケープされていないため、自動エスケープを有効にする
            List<String> insertSqlList = insertSqlBuilder.build(tableName, columnDefs, batch);
            appendSqlFile(insertSqlFilePath, String.join(System.lineSeparator(), insertSqlList));
        }
    }

    private void createDirectoryIfAbsent(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.directoryCreateFailed", dir), e);
        }
    }

    private void deleteIfExists(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileWriteFailed", filePath), e);
        }
    }

    private void appendSqlFile(Path filePath, String content) {
        if (content.isEmpty()) {
            return;
        }
        try {
            Files.writeString(filePath, content + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(filePath) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileWriteFailed", filePath), e);
        }
    }
}
