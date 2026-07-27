package com.freedom.taskall_v2.web.exception;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;
import com.freedom.taskall_v2.web.controller.TaskallV2Controller;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

/**
 * {@link GlobalExceptionHandler}のテストです。
 *
 * <p>
 * {@code TaskallV2Controller}経由で例外を発生させ、{@link GlobalExceptionHandler}が
 * エラー画面へ遷移させることを確認します。
 * </p>
 */
@WebMvcTest(TaskallV2Controller.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestHandlingService requestHandlingService;

    @MockitoBean
    private MsgUtil msg;

    @Test
    void 業務例外が発生した場合はエラー画面のビュー名が返却されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenThrow(new BusinessRuleViolationException("必須パラメータが指定されていません。"));

        mockMvc.perform(get("/taskall-v2/service/top.html"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"));
    }

    @Test
    void システム例外が発生した場合はエラー画面のビュー名が返却されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenThrow(new ApplicationInternalException("JSON処理に失敗しました。"));

        mockMvc.perform(get("/taskall-v2/service/top.html"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"));
    }

    @Test
    void 予期せぬ実行時例外が発生した場合はエラー画面のビュー名が返却されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenThrow(new IllegalStateException("予期せぬ状態です。"));

        mockMvc.perform(get("/taskall-v2/service/top.html"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"));
    }
}
