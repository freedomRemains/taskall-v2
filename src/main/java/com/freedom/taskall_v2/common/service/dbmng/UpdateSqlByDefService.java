package com.freedom.taskall_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.CreateTableSqlBuilder;
import com.freedom.taskall_v2.common.db.DropTableSqlBuilder;
import com.freedom.taskall_v2.common.db.SelectSqlBuilder;
import com.freedom.taskall_v2.common.db.TableDefLoader;
import com.freedom.taskall_v2.common.db.sqlite.SqlitePrimaryKeyColumnSqlBuilder;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 作業ディレクトリに配置されたテーブル定義ファイル({@code data/TBL_DEF.txt})から、
 * DROP／CREATE／SELECT文を生成し、実行用SQLディレクトリへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code UpdateSqlByDefService}に相当します。DB構成更新(リストア)の
 * 最初の段階として、スキーマ側(テーブルの再作成)のSQLのみを生成します。データ側(INSERT文)は
 * {@link UpdateSqlByDataService}が生成します。
 * </p>
 *
 * <p>
 * {@code TBL_DEF.txt}はTBL_DEF自身も他のテーブルと同様に{@link GetAllTableDataService}が
 * {@code data}ディレクトリへ書き出すため、専用のディレクトリは持たず、他のテーブルデータと
 * 同じ{@code data}ディレクトリ配下のファイルをそのまま読み込みます。
 * </p>
 */
@Service
public class UpdateSqlByDefService implements ScriptElementService {

    private static final String DATA_DIR_NAME = "data";
    private static final String SQL_DIR_NAME = "sql";
    private static final String TBL_DEF_FILE_NAME = "TBL_DEF.txt";

    private final DbMngProperties dbMngProperties;
    private final TableDefLoader tableDefLoader;
    private final DropTableSqlBuilder dropTableSqlBuilder;
    private final CreateTableSqlBuilder createTableSqlBuilder;
    private final SelectSqlBuilder selectSqlBuilder;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public UpdateSqlByDefService(DbMngProperties dbMngProperties, ObjectMapper objectMapper, MsgUtil msg) {
        this(dbMngProperties, new TableDefLoader(), new DropTableSqlBuilder(), new CreateTableSqlBuilder(msg,
                new SqlitePrimaryKeyColumnSqlBuilder()),
                new SelectSqlBuilder(msg), objectMapper, msg);
    }

    public UpdateSqlByDefService(DbMngProperties dbMngProperties, TableDefLoader tableDefLoader,
            DropTableSqlBuilder dropTableSqlBuilder, CreateTableSqlBuilder createTableSqlBuilder,
            SelectSqlBuilder selectSqlBuilder, ObjectMapper objectMapper, MsgUtil msg) {
        this.dbMngProperties = dbMngProperties;
        this.tableDefLoader = tableDefLoader;
        this.dropTableSqlBuilder = dropTableSqlBuilder;
        this.createTableSqlBuilder = createTableSqlBuilder;
        this.selectSqlBuilder = selectSqlBuilder;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // 入力コンテキストは使用しないが、他のスクリプト要素サービスとインターフェースを揃えるため受け取る
        DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);

        Path workDir = Path.of(dbMngProperties.getWorkDir());
        Path tblDefFilePath = workDir.resolve(DATA_DIR_NAME).resolve(TBL_DEF_FILE_NAME);

        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap =
                tableDefLoader.load(tblDefFilePath);

        Path sqlDir = workDir.resolve(SQL_DIR_NAME);
        createDirectoryIfAbsent(sqlDir);

        ArrayNode tableNameList = objectMapper.createArrayNode();
        for (Map.Entry<String, ArrayList<LinkedHashMap<String, String>>> entry : tableDefMap.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, String>> columnDefs = new ArrayList<>(entry.getValue());

            writeSqlFile(sqlDir.resolve("DROP_" + tableName + ".sql"), dropTableSqlBuilder.build(tableName));
            writeSqlFile(sqlDir.resolve("CREATE_" + tableName + ".sql"),
                    createTableSqlBuilder.build(tableName, columnDefs));
            writeSqlFile(sqlDir.resolve("SELECT_" + tableName + ".sql"),
                    selectSqlBuilder.build(tableName, columnDefs));

            tableNameList.add(tableName);
        }

        // 後続のUpdateSqlByDataService/UpdateAllTableServiceが同じテーブル順序を使い回せるよう、
        // TBL_DEF.txt上のテーブル出現順をそのままtableNameListとして引き継ぐ
        ObjectNode output = objectMapper.createObjectNode();
        output.set("tableNameList", tableNameList);
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
