package com.freedom.taskall_v2.web.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.web.controller.TaskallV2Controller;
import com.freedom.taskall_v2.web.service.RequestHandlingService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link ControllerLoggingAspect}のテストです。
 *
 * <p>
 * コントローラは{@code @Controller}が付与されたSpring管理のBeanであり、AOPプロキシが
 * 実際に適用されるかはMockitoによる単体テストでは確認できないため、実際のアプリケーション
 * コンテキストとMockMvcを用いて、コントローラ呼び出し後に処理時間がログ出力されることを確認する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControllerLoggingAspectTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskallV2Controller taskallV2Controller;

    @MockitoBean
    private RequestHandlingService requestHandlingService;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        ((Logger) org.slf4j.LoggerFactory.getLogger(ControllerLoggingAspect.class)).addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) org.slf4j.LoggerFactory.getLogger(ControllerLoggingAspect.class)).detachAppender(listAppender);
    }

    @Test
    void コントローラがAOPプロキシとして注入されていること() {
        assertThat(AopUtils.isAopProxy(taskallV2Controller)).isTrue();
    }

    @Test
    void コントローラ実行後に処理時間がログ出力されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"top.html\"}");

        mockMvc.perform(get("/taskall-v2/service/top.html")).andExpect(status().is3xxRedirection());

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(events.get(0).getFormattedMessage())
                .contains("[ControllerExecutionTime]").contains("getTop").contains("elapsedMillis=");
    }
}
