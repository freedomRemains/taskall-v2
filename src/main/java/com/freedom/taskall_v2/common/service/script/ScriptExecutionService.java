package com.freedom.taskall_v2.common.service.script;

import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.common.util.VariablePlaceholderResolver;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * スクリプト(SCR/SCR_ELM)に基づいて、複数の{@link ScriptElementService}を連続実行するクラスです。
 *
 * <p>
 * 移植元「remainz」の{@code ScriptService}に相当します。{@code SCR_PRM}によるスクリプトパラメータの
 * 投入、及び{@code #{key}}形式のプレースホルダー置換ロジックを移植しています。アダプタ処理
 * ({@code AdapterInterface}/{@code GenericAdapter})は移植対象外です。
 * </p>
 */
@Service
public class ScriptExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ScriptExecutionService.class);

    private static final String SCR_ELM_SQL = """
            SELECT A.SCR_NAME, B.SCR_ELM_ID, B.SERVICE_NAME, B.ORD_IN_GRP
            FROM SCR A
            INNER JOIN SCR_ELM B ON A.SCR_ID = B.SCR_ID
            WHERE A.SCR_ID = ?
            ORDER BY B.ORD_IN_GRP
            """;

    private static final String SCR_PRM_SQL = """
            SELECT A.SCR_NAME, B.SCR_PRM_ID, B.PARAM_KEY, B.PARAM_VALUE, B.ORD_IN_GRP
            FROM SCR A
            INNER JOIN SCR_PRM B ON A.SCR_ID = B.SCR_ID
            WHERE A.SCR_ID = ?
            ORDER BY B.ORD_IN_GRP
            """;

    private final RecordQueryService recordQueryService;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public ScriptExecutionService(RecordQueryService recordQueryService, ApplicationContext applicationContext,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    /**
     * スクリプトIDに紐づく{@code SCR_ELM}を{@code ORD_IN_GRP}順に実行します。
     *
     * @param scriptId       スクリプトID({@code SCR_ID})
     * @param initialContext 実行前のコンテキストを表すJSON文字列
     * @return 全てのスクリプト要素を実行した後のコンテキストを表すJSON文字列
     */
    public String execute(String scriptId, String initialContext) {

        // 入力JSONをオブジェクト化し、スクリプト実行前にSCR_PRMの値をコンテキストへ反映する
        ObjectNode context = readAsObjectNode(initialContext);

        applyScriptParams(scriptId, context);

        // SCR_ELMを定義順に読み込み、各サービスの出力JSONを現在のコンテキストへマージする
        List<LinkedHashMap<String, String>> elementList = recordQueryService.select(SCR_ELM_SQL, List.of(scriptId));

        for (LinkedHashMap<String, String> row : elementList) {
            String serviceName = row.get("SERVICE_NAME");
            ScriptElementService service = instantiate(serviceName);
            logger.info("スクリプト要素を実行します。scrElmId={}, serviceName={}", row.get("SCR_ELM_ID"), serviceName);

            String resultJson = service.execute(writeAsString(context));
            ObjectNode resultNode = readAsObjectNode(resultJson);
            context.setAll(resultNode);
        }

        return writeAsString(context);
    }

    private void applyScriptParams(String scriptId, ObjectNode context) {

        // SCR_PRMを定義順に読み込み、プレースホルダー解決後の値を投入候補として準備する
        List<LinkedHashMap<String, String>> paramList = recordQueryService.select(SCR_PRM_SQL, List.of(scriptId));

        for (LinkedHashMap<String, String> row : paramList) {
            String paramKey = row.get("PARAM_KEY");
            String paramValue = VariablePlaceholderResolver.resolve(row.get("PARAM_VALUE"), context, msg);

            // 既に入力コンテキスト側で値が設定されているキーは、警告のみ記録して上書きしない
            JsonNode existing = context.get(paramKey);
            if (existing != null && !existing.isNull() && !existing.asString().isEmpty()) {
                logger.warn(msg.get("msg.warn.web.scriptParamAlreadyExists", paramKey, paramValue));
                continue;
            }

            context.put(paramKey, paramValue);
        }
    }

    private ScriptElementService instantiate(String serviceName) {
        try {
            // クラス名からSpring管理Beanを取得し、スクリプト要素サービスとして利用する
            Class<?> serviceClass = Class.forName(serviceName);
            return (ScriptElementService) applicationContext.getBean(serviceClass);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.serviceInstantiationFailed", serviceName), e);
        }
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
