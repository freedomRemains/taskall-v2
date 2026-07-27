package com.freedom.taskall_v2.common.service.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ScriptExecutionService}のテストです。
 *
 * <p>
 * {@link RecordQueryService}と{@link ApplicationContext}のみをモックし、
 * {@link ScriptElementService}の実体としてこのテストクラス内のスタブ実装を使用します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ScriptExecutionServiceTest {

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

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private ApplicationContext applicationContext;

    private ScriptExecutionService scriptExecutionService;

    @BeforeEach
    void setUp() {
        scriptExecutionService = new ScriptExecutionService(
                recordQueryService, applicationContext, JsonMapper.builder().build(), new MsgUtil());
    }

    /**
     * コンテキストに追加した文字列値をそのまま出力に反映する、テスト用のスクリプト要素です。
     */
    static class StubScriptElementService implements ScriptElementService {

        private final JsonMapper jsonMapper = JsonMapper.builder().build();

        @Override
        public String execute(String contextJson) {
            var context = jsonMapper.readTree(contextJson);
            var output = jsonMapper.createObjectNode();
            output.put("greeting", "hello " + context.path("name").asString(""));
            return jsonMapper.writeValueAsString(output);
        }
    }

    @Test
    void SCR_ELM順にサービスが実行されコンテキストがマージされること() {

        LinkedHashMap<String, String> elementRow = new LinkedHashMap<>();
        elementRow.put("SCR_ELM_ID", "1100001");
        elementRow.put("SERVICE_NAME", StubScriptElementService.class.getName());
        elementRow.put("ORD_IN_GRP", "1");

        when(recordQueryService.select(eq(SCR_PRM_SQL), eq(List.of("1100001")))).thenReturn(new java.util.ArrayList<>());
        when(recordQueryService.select(eq(SCR_ELM_SQL), eq(List.of("1100001"))))
                .thenReturn(new java.util.ArrayList<>(List.of(elementRow)));
        when(applicationContext.getBean(StubScriptElementService.class)).thenReturn(new StubScriptElementService());

        String result = scriptExecutionService.execute("1100001", "{\"name\":\"world\"}");

        assertThat(result).contains("\"greeting\":\"hello world\"");
        assertThat(result).contains("\"name\":\"world\"");
    }

    @Test
    void SCR_PRMのパラメータが未設定のキーにのみ入力に反映されること() {

        LinkedHashMap<String, String> paramRow = new LinkedHashMap<>();
        paramRow.put("PARAM_KEY", "basePath");
        paramRow.put("PARAM_VALUE", "/taskall-v2/");
        paramRow.put("ORD_IN_GRP", "1");

        when(recordQueryService.select(eq(SCR_PRM_SQL), eq(List.of("1000001"))))
                .thenReturn(new java.util.ArrayList<>(List.of(paramRow)));
        when(recordQueryService.select(eq(SCR_ELM_SQL), eq(List.of("1000001")))).thenReturn(new java.util.ArrayList<>());

        String result = scriptExecutionService.execute("1000001", "{}");

        assertThat(result).contains("\"basePath\":\"/taskall-v2/\"");
    }

    @Test
    void 存在しないサービスクラス指定時はApplicationInternalExceptionがスローされること() {

        LinkedHashMap<String, String> elementRow = new LinkedHashMap<>();
        elementRow.put("SCR_ELM_ID", "9999999");
        elementRow.put("SERVICE_NAME", "com.freedom.taskall_v2.web.service.NotExistService");
        elementRow.put("ORD_IN_GRP", "1");

        when(recordQueryService.select(eq(SCR_PRM_SQL), eq(List.of("9999999")))).thenReturn(new java.util.ArrayList<>());
        when(recordQueryService.select(eq(SCR_ELM_SQL), eq(List.of("9999999"))))
                .thenReturn(new java.util.ArrayList<>(List.of(elementRow)));

        assertThatThrownBy(() -> scriptExecutionService.execute("9999999", "{}"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
