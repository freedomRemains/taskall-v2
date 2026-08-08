package com.freedom.taskall_v2.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link TaskallV2Controller}のテストです。
 */
@WebMvcTest(TaskallV2Controller.class)
class TaskallV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestHandlingService requestHandlingService;

    @MockitoBean
    private MsgUtil msg;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        ((Logger) org.slf4j.LoggerFactory.getLogger(TaskallV2Controller.class)).addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) org.slf4j.LoggerFactory.getLogger(TaskallV2Controller.class)).detachAppender(listAppender);
    }

    @Test
    void healthzは業務ロジックを呼び出さずOKを返すこと() throws Exception {

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        org.mockito.Mockito.verifyNoInteractions(requestHandlingService);
    }

    @Test
    void SpringFramework関連のリクエスト属性はAttributesログから除外されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"top.html\"}");

        mockMvc.perform(get("/taskall-v2/service/top.html")
                        .requestAttr("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern",
                                "/taskall-v2/service/top.html")
                        .requestAttr("customAttribute", "customValue"))
                .andExpect(status().is3xxRedirection());

        List<ILoggingEvent> events = listAppender.list;
        String attributesLog = events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("[Attributes]"))
                .findFirst()
                .orElseThrow();

        assertThat(attributesLog).doesNotContain("springframework");
        assertThat(attributesLog).contains("customAttribute: customValue");
    }

    @Test
    void トップページのGETリクエストで応答種別forwardの場合はビュー名が拡張子無しで解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/top.html"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"))
                .andExpect(model().attribute("respKind", "forward"))
                .andExpect(model().attributeExists("htmlPage"));
    }

    @Test
    void 応答種別がredirectの場合はredirectプレフィックス付きのビュー名が返却されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"top.html\"}");

        mockMvc.perform(get("/taskall-v2/service/top.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:top.html"));
    }

    @Test
    void マイページのGETリクエストで応答種別forwardの場合はビュー名が拡張子無しで解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000201\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/myPage.html"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void マイページのレンダリング結果に不要なbodyタグが混入しないこと() throws Exception {

        // issue #15: parts/配下のフラグメントテンプレートがDOCTYPE/html/bodyを持つ完全な
        // HTML文書だったため、th:replaceでの参照先selectionにbodyタグが漏れ出す不具合があった
        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000201\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        String responseBody = mockMvc.perform(get("/taskall-v2/service/myPage.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // ページ全体としてbody開始/終了タグはそれぞれ1個のみ出現するべき
        assertThat(org.springframework.util.StringUtils.countOccurrencesOf(responseBody, "<body")).isEqualTo(1);
        assertThat(org.springframework.util.StringUtils.countOccurrencesOf(responseBody, "</body>")).isEqualTo(1);
    }

    @Test
    void テーブルデータメンテナンス画面のPOSTリクエストで一括削除が実行されビュー名が解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(post("/taskall-v2/service/tableDataMainte.html")
                        .param("tableName", "ACCNT")
                        .param("1000002", "on"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void 新規レコード追加画面のGETリクエストでビュー名が解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/tableDataMainte/newRecord.html").param("tableName", "ACCNT"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void 新規レコード追加画面のPOSTリクエストで応答種別redirectの場合はredirectプレフィックス付きのビュー名が返却されること()
            throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"redirect\",\"destination\":\"tableDataMainte.html?tableName=ACCNT\"}");

        mockMvc.perform(post("/taskall-v2/service/tableDataMainte/newRecord.html").param("tableName", "ACCNT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:tableDataMainte.html?tableName=ACCNT"));
    }

    @Test
    void レコード編集画面のGETリクエストでビュー名が解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/tableDataMainte/editRecord.html")
                        .param("tableName", "ACCNT").param("recordId", "1000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void レコード編集画面のPOSTリクエストで応答種別redirectの場合はredirectプレフィックス付きのビュー名が返却されること()
            throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"redirect\",\"destination\":\"tableDataMainte/editRecord.html?tableName=ACCNT"
                        + "&recordId=1000001&errMsgKey=123\"}");

        mockMvc.perform(post("/taskall-v2/service/tableDataMainte/editRecord.html")
                        .param("tableName", "ACCNT").param("recordId", "1000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:tableDataMainte/editRecord.html?tableName=ACCNT"
                        + "&recordId=1000001&errMsgKey=123"));
    }

    @Test
    void レコード削除画面のGETリクエストでビュー名が解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/tableDataMainte/deleteRecord.html")
                        .param("tableName", "ACCNT").param("recordId", "1000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void レコード削除画面のPOSTリクエストで応答種別redirectの場合はredirectプレフィックス付きのビュー名が返却されること()
            throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"redirect\",\"destination\":\"tableDataMainte.html?tableName=ACCNT&errMsgKey=123\"}");

        mockMvc.perform(post("/taskall-v2/service/tableDataMainte/deleteRecord.html")
                        .param("tableName", "ACCNT").param("recordId", "1000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:tableDataMainte.html?tableName=ACCNT&errMsgKey=123"));
    }

    @Test
    void レコード参照画面のGETリクエストでビュー名が解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"htmlPartsId\":\"1000001\","
                        + "\"items\":[{\"itemKey\":\"systemName\",\"records\":[{\"GNR_VAL\":\"Taskall\"}]}]}],"
                        + "\"account\":[{\"ACCNT_ID\":\"1000001\",\"ACCOUNT_NAME\":\"ゲスト\"}],"
                        + "\"authList\":[{\"HTML_PARTS_ID\":\"1000001\",\"AUTH_KIND\":\"read\"}]}");

        mockMvc.perform(get("/taskall-v2/service/recordRef.html")
                        .param("tableName", "ACCNT").param("recordId", "1000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void セッションにpendingTwoFactorAccountIdがある場合は入力コンテキストへ転記されModelへも設定されること() {

        TaskallV2Controller controller = new TaskallV2Controller(requestHandlingService, new ObjectMapper(), msg);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/taskall-v2/service/twoFactorAuth.html");
        request.getSession(true).setAttribute("pendingTwoFactorAccountId", "1000001");
        Model model = new ExtendedModelMap();

        when(requestHandlingService.execute(any())).thenAnswer(invocation -> {
            String inputJson = invocation.getArgument(0);
            assertThat(inputJson).contains("\"pendingTwoFactorAccountId\":\"1000001\"");
            return "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\"}";
        });

        controller.getTwoFactorAuth(request, model);

        assertThat(model.getAttribute("pendingTwoFactorAccountId")).isEqualTo("1000001");
    }

    @Test
    void twoFactorAuthCompletedがtrueの場合はpendingTwoFactorAccountIdセッション属性が削除されること() {

        TaskallV2Controller controller = new TaskallV2Controller(requestHandlingService, new ObjectMapper(), msg);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/taskall-v2/service/twoFactorAuth.html");
        request.getSession(true).setAttribute("pendingTwoFactorAccountId", "1000001");
        Model model = new ExtendedModelMap();

        when(requestHandlingService.execute(any()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"/taskall-v2/service/myPage.html\","
                        + "\"twoFactorAuthCompleted\":true,\"account\":[{\"ACCNT_ID\":\"1000001\"}]}");

        controller.postTwoFactorAuth(request, model);

        assertThat(request.getSession(false).getAttribute("pendingTwoFactorAccountId")).isNull();
    }
}
