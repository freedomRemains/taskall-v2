package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.service.script.ScriptExecutionService;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link RequestHandlingService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class RequestHandlingServiceTest {

    private static final String HTML_PAGE_SQL = """
            SELECT
                A.HTML_PAGE_ID, A.PAGE_NAME, A.SCR_ID_GET, A.SCR_ID_POST,
                A.SCR_ID_PUT, A.SCR_ID_DELETE
            FROM HTML_PAGE A
            LEFT JOIN URI_PATTERN B ON A.URI_PATTERN_ID = B.URI_PATTERN_ID
            WHERE B.URI_PATTERN = ?
            """;

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private ScriptExecutionService scriptExecutionService;

    private RequestHandlingService requestHandlingService;

    @BeforeEach
    void setUp() {
        requestHandlingService = new RequestHandlingService(recordQueryService, scriptExecutionService,
                JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> pageRow(String scrIdGet) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("HTML_PAGE_ID", "1000001");
        row.put("PAGE_NAME", "TOP");
        row.put("SCR_ID_GET", scrIdGet);
        row.put("SCR_ID_POST", "0");
        row.put("SCR_ID_PUT", "0");
        row.put("SCR_ID_DELETE", "0");
        return row;
    }

    @Test
    void リクエスト種別に対応するスクリプトIDでスクリプトが実行されること() {

        when(recordQueryService.select(eq(HTML_PAGE_SQL), eq(List.of("/taskall-v2/service/top.html"))))
                .thenReturn(new ArrayList<>(List.of(pageRow("1100001"))));
        when(scriptExecutionService.execute(eq("1100001"), eq(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/top.html\"}")))
                .thenReturn("{\"respKind\":\"forward\"}");

        String result = requestHandlingService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/top.html\"}");

        assertThat(result).isEqualTo("{\"respKind\":\"forward\"}");
        verify(scriptExecutionService).execute("1100001",
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/top.html\"}");
    }

    @Test
    void リクエストURIに対応するページが存在しない場合は例外がスローされること() {

        when(recordQueryService.select(eq(HTML_PAGE_SQL), eq(List.of("/taskall-v2/service/unknown.html"))))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> requestHandlingService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/unknown.html\"}"))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void リクエスト種別に対応するスクリプトIDが未設定の場合は例外がスローされること() {

        when(recordQueryService.select(eq(HTML_PAGE_SQL), eq(List.of("/taskall-v2/service/top.html"))))
                .thenReturn(new ArrayList<>(List.of(pageRow("0"))));

        assertThatThrownBy(() -> requestHandlingService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/taskall-v2/service/top.html\"}"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
