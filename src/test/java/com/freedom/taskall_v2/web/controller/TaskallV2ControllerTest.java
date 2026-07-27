package com.freedom.taskall_v2.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

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
    void マイページのPOSTリクエストで応答種別redirectの場合はredirectプレフィックス付きのビュー名が返却されること()
            throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"myPage.html?errMsgKey=5\"}");

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .param("MAIL_ADDRESS", "wrong@account.com")
                        .param("PASSWORD", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:myPage.html?errMsgKey=5"));
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
}
