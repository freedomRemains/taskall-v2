package com.freedom.taskall_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.CreateTableSqlBuilder;
import com.freedom.taskall_v2.common.db.sqlite.SqlitePrimaryKeyColumnSqlBuilder;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code tableNameList}の全テーブルについて、ライブDBのカラム定義からCREATE TABLE文を生成し、
 * 作業ディレクトリへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetTableCreateSqlService}/{@code GetAllTableCreateSqlService}に
 * 相当します。
 * </p>
 */
@Service
public class GetAllTableCreateSqlService implements ScriptElementService {

    private static final String SQL_DIR_NAME = "sql";

    private final LiveTableColumnDefLoader liveTableColumnDefLoader;
    private final DbMngProperties dbMngProperties;
    private final CreateTableSqlBuilder createTableSqlBuilder;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public GetAllTableCreateSqlService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            DbMngProperties dbMngProperties, ObjectMapper objectMapper, MsgUtil msg) {
        this(liveTableColumnDefLoader, dbMngProperties, new CreateTableSqlBuilder(msg,
                new SqlitePrimaryKeyColumnSqlBuilder()), objectMapper, msg);
    }

    public GetAllTableCreateSqlService(LiveTableColumnDefLoader liveTableColumnDefLoader,
            DbMngProperties dbMngProperties, CreateTableSqlBuilder createTableSqlBuilder, ObjectMapper objectMapper,
            MsgUtil msg) {
        this.liveTableColumnDefLoader = liveTableColumnDefLoader;
        this.dbMngProperties = dbMngProperties;
        this.createTableSqlBuilder = createTableSqlBuilder;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path sqlDir = Path.of(dbMngProperties.getWorkDir()).resolve(SQL_DIR_NAME);
        createDirectoryIfAbsent(sqlDir);

        // テーブルごとにライブDBのカラム定義を取得し、CREATE TABLE文を生成して書き出す
        for (String tableName : tableNameList) {
            List<Map<String, String>> columnDefs = liveTableColumnDefLoader.load(tableName);
            writeSqlFile(sqlDir.resolve("CREATE_" + tableName + ".sql"),
                    createTableSqlBuilder.build(tableName, columnDefs));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("createSqlDirPath", sqlDir.toString());
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
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
