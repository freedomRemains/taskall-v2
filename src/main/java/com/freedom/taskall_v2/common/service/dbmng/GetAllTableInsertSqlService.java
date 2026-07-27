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
import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code tableNameList}の全テーブルについて、ライブDBのデータからINSERT文を生成し、
 * 作業ディレクトリへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetTableInsertSqlService}/{@code GetAllTableInsertSqlService}に
 * 相当します。{@link GetAllTableDataService}と同様、テーブル全件を一度にメモリへ保持しないよう、
 * {@link #BATCH_SIZE}件ずつページング取得しながらINSERT文を生成し、都度ファイルへ追記します。
 * </p>
 */
@Service
public class GetAllTableInsertSqlService implements ScriptElementService {

    /** 1回のページング取得・INSERT文生成あたりの最大件数 */
    static final int BATCH_SIZE = 5000;

    private static final String SQL_DIR_NAME = "sql";

    private final LiveTableColumnDefLoader liveTableColumnDefLoader;
    private final RecordQueryService recordQueryService;
    private final DbMngProperties dbMngProperties;
    private final InsertSqlBuilder insertSqlBuilder;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public GetAllTableInsertSqlService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            RecordQueryService recordQueryService, DbMngProperties dbMngProperties, ObjectMapper objectMapper,
            MsgUtil msg) {
        this(liveTableColumnDefLoader, recordQueryService, dbMngProperties, new InsertSqlBuilder(msg), objectMapper,
                msg);
    }

    public GetAllTableInsertSqlService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            RecordQueryService recordQueryService, DbMngProperties dbMngProperties,
            InsertSqlBuilder insertSqlBuilder, ObjectMapper objectMapper, MsgUtil msg) {
        this.liveTableColumnDefLoader = liveTableColumnDefLoader;
        this.recordQueryService = recordQueryService;
        this.dbMngProperties = dbMngProperties;
        this.insertSqlBuilder = insertSqlBuilder;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path sqlDir = Path.of(dbMngProperties.getWorkDir()).resolve(SQL_DIR_NAME);
        createDirectoryIfAbsent(sqlDir);

        // テーブルごとにページング取得しながらINSERT文を生成し、都度ファイルへ追記していく
        for (String tableName : tableNameList) {
            writeInsertSql(sqlDir, tableName);
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("insertSqlDirPath", sqlDir.toString());
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }

    private void writeInsertSql(Path sqlDir, String tableName) {

        List<Map<String, String>> columnDefs = liveTableColumnDefLoader.load(tableName);
        List<String> columnNames = columnNames(columnDefs);
        Path insertSqlFilePath = sqlDir.resolve("INSERT_" + tableName + ".sql");

        // 前回実行時のファイルが残っていると内容が重複して追記されてしまうため、先に削除しておく
        deleteIfExists(insertSqlFilePath);

        // 1バッチの取得件数がBATCH_SIZE未満になるまで、offsetをずらしながらページング取得する
        int offset = 0;
        while (true) {
            List<LinkedHashMap<String, String>> batch = selectBatch(tableName, columnNames, offset);
            if (batch.isEmpty()) {
                break;
            }

            // ライブDBの値はseedデータのように事前エスケープされていないため、自動エスケープを有効にする
            List<String> insertSqlList = insertSqlBuilder.build(tableName, columnDefs, new ArrayList<>(batch));
            appendSqlFile(insertSqlFilePath, String.join(System.lineSeparator(), insertSqlList));

            if (batch.size() < BATCH_SIZE) {
                break;
            }
            offset += BATCH_SIZE;
        }
    }

    private List<LinkedHashMap<String, String>> selectBatch(String tableName, List<String> columnNames, int offset) {
        String columnPart = String.join(", ", columnNames);
        String sql = "SELECT " + columnPart + " FROM " + tableName + " ORDER BY " + columnPart
                + " LIMIT " + BATCH_SIZE + " OFFSET " + offset;
        return recordQueryService.select(sql);
    }

    private List<String> columnNames(List<Map<String, String>> columnDefs) {
        List<String> columnNames = new ArrayList<>();
        for (Map<String, String> columnDef : columnDefs) {
            columnNames.add(columnDef.get("FIELD_NAME"));
        }
        return columnNames;
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
