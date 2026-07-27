package com.freedom.taskall_v2.common.service.dbmng;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freedom.taskall_v2.common.config.DbMngProperties;
import com.freedom.taskall_v2.common.db.SqlFileExecutionService;
import com.freedom.taskall_v2.common.service.script.ScriptElementService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link UpdateSqlByDefService}/{@link UpdateSqlByDataService}が生成したSQLファイル一式
 * ({@code sql}配下)を、DROP→CREATE→INSERTの順に実行するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code UpdateAllTableService}に相当します。実行順は{@code DbInitializer}/
 * {@code DbInitializationService}と同様、依存関係を崩さないようテーブル横断でDROP→CREATE→INSERTの
 * 3段階に分けて実行します。commit/rollbackは本メソッドに付与する{@link Transactional}で
 * Spring管理とするため、明示的なcommit/rollback処理は行いません。
 * </p>
 */
@Service
public class UpdateAllTableService implements ScriptElementService {

    private static final String SQL_DIR_NAME = "sql";

    private final DbMngProperties dbMngProperties;
    private final SqlFileExecutionService sqlFileExecutionService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public UpdateAllTableService(DbMngProperties dbMngProperties, SqlFileExecutionService sqlFileExecutionService,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.dbMngProperties = dbMngProperties;
        this.sqlFileExecutionService = sqlFileExecutionService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    @Transactional
    public String execute(String contextJson) {

        ObjectNode context = DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);
        List<String> tableNameList = DbMngJsonUtil.readTableNameList(context);

        Path sqlDir = Path.of(dbMngProperties.getWorkDir()).resolve(SQL_DIR_NAME);

        // 依存関係を崩さないよう、DROP→CREATE→INSERTをそれぞれ定義順で順次実行する
        for (String tableName : tableNameList) {
            sqlFileExecutionService.execute(sqlDir.resolve("DROP_" + tableName + ".sql"));
        }
        for (String tableName : tableNameList) {
            sqlFileExecutionService.execute(sqlDir.resolve("CREATE_" + tableName + ".sql"));
        }
        for (String tableName : tableNameList) {
            sqlFileExecutionService.execute(sqlDir.resolve("INSERT_" + tableName + ".sql"));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("dbUpdateCompleted", true);
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }
}
