package com.freedom.taskall_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.DropTableSqlBuilder;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code tableNameList}の全テーブルについて、DROP TABLE文を生成し、作業ディレクトリへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetTableDropSqlService}/{@code GetAllTableDropSqlService}に相当します。
 * DROP文はテーブル物理名のみから決まるため、カラム定義の取得は不要です。
 * </p>
 */
@Service
public class GetAllTableDropSqlService implements ScriptElementService {

    private static final String SQL_DIR_NAME = "sql";

    private final DbMngProperties dbMngProperties;
    private final DropTableSqlBuilder dropTableSqlBuilder;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public GetAllTableDropSqlService(DbMngProperties dbMngProperties, ObjectMapper objectMapper, MsgUtil msg) {
        this(dbMngProperties, new DropTableSqlBuilder(), objectMapper, msg);
    }

    public GetAllTableDropSqlService(DbMngProperties dbMngProperties, DropTableSqlBuilder dropTableSqlBuilder,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.dbMngProperties = dbMngProperties;
        this.dropTableSqlBuilder = dropTableSqlBuilder;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path sqlDir = Path.of(dbMngProperties.getWorkDir()).resolve(SQL_DIR_NAME);
        createDirectoryIfAbsent(sqlDir);

        // テーブルごとにDROP TABLE文を生成し、後続のDB構成更新(リストア)で実行できるよう書き出す
        for (String tableName : tableNameList) {
            writeSqlFile(sqlDir.resolve("DROP_" + tableName + ".sql"), dropTableSqlBuilder.build(tableName));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("dropSqlDirPath", sqlDir.toString());
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
