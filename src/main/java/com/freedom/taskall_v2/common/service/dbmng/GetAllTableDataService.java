package com.freedom.taskall_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.db.TsvTableFileWriter;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code tableNameList}の全テーブルについて、ライブDBのデータをTSVファイルへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetTableDataService}/{@code GetAllTableDataService}に相当します。
 * 移植元はテーブル全件を一度に{@code ArrayList}へ読み込むため大量データでOOMのおそれがありますが、
 * 本実装では{@link #BATCH_SIZE}件ずつページング取得して都度ファイルへ追記することで、
 * テーブル全体を一度にメモリへ保持しない構成としています。
 * </p>
 */
@Service
public class GetAllTableDataService implements ScriptElementService {

    /** 1回のページング取得あたりの最大件数 */
    static final int BATCH_SIZE = 5000;

    private static final String DATA_DIR_NAME = "data";

    private final LiveTableColumnDefLoader liveTableColumnDefLoader;
    private final RecordQueryService recordQueryService;
    private final DbMngProperties dbMngProperties;
    private final TsvTableFileWriter tsvTableFileWriter;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public GetAllTableDataService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            RecordQueryService recordQueryService, DbMngProperties dbMngProperties, ObjectMapper objectMapper,
            MsgUtil msg) {
        this(liveTableColumnDefLoader, recordQueryService, dbMngProperties, new TsvTableFileWriter(msg),
                objectMapper, msg);
    }

    public GetAllTableDataService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            RecordQueryService recordQueryService, DbMngProperties dbMngProperties,
            TsvTableFileWriter tsvTableFileWriter, ObjectMapper objectMapper, MsgUtil msg) {
        this.liveTableColumnDefLoader = liveTableColumnDefLoader;
        this.recordQueryService = recordQueryService;
        this.dbMngProperties = dbMngProperties;
        this.tsvTableFileWriter = tsvTableFileWriter;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path dbDataDir = Path.of(dbMngProperties.getWorkDir()).resolve(DATA_DIR_NAME);
        createDirectoryIfAbsent(dbDataDir);

        // テーブルごとにページング取得しながら、都度TSVファイルへ追記していく
        for (String tableName : tableNameList) {
            writeTableData(dbDataDir, tableName);
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("dbDataDirPath", dbDataDir.toString());
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }

    private void writeTableData(Path dbDataDir, String tableName) {

        List<String> columnNames = columnNames(liveTableColumnDefLoader.load(tableName));
        Path dataFilePath = dbDataDir.resolve(tableName + ".txt");

        // 前回実行時のファイルが残っていると内容が重複して追記されてしまうため、先に削除しておく
        deleteIfExists(dataFilePath);

        // 1バッチの取得件数がBATCH_SIZE未満になるまで、offsetをずらしながらページング取得する
        int offset = 0;
        while (true) {
            List<LinkedHashMap<String, String>> batch = selectBatch(tableName, columnNames, offset);
            if (batch.isEmpty()) {
                break;
            }
            tsvTableFileWriter.append(dataFilePath, batch);
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
}
