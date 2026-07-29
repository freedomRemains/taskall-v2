package com.freedom.taskall_v2.common.db.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.freedom.taskall_v2.common.db.RecordQueryService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link SqlLoggingAspect}のテストです。
 *
 * <p>
 * {@code JdbcTemplate}は{@code RecordQueryService}をはじめとする各サービスから直接注入されて
 * 利用される、Spring管理のBeanです。Mockitoでの単体テストではAOPプロキシの実際の適用有無を
 * 確認できないため、実際のアプリケーションコンテキストを起動して{@code JdbcTemplate}が
 * AOPプロキシ化されていること、及びSQL実行前後のログが記録されることを確認する。
 * </p>
 */
@SpringBootTest
class SqlLoggingAspectTest {

    @Autowired
    private RecordQueryService recordQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        ((Logger) org.slf4j.LoggerFactory.getLogger(SqlLoggingAspect.class)).addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) org.slf4j.LoggerFactory.getLogger(SqlLoggingAspect.class)).detachAppender(listAppender);
    }

    @Test
    void JdbcTemplateがAOPプロキシとして注入されていること() {
        assertThat(AopUtils.isAopProxy(jdbcTemplate)).isTrue();
    }

    @Test
    void SELECT実行前後にSQL文と所要時間がログ出力されること() {

        recordQueryService.select("SELECT 1 AS DUMMY");

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(events.get(0).getFormattedMessage()).contains("[SqlExecution]").contains("SELECT 1 AS DUMMY");
        assertThat(events.get(1).getLevel()).isEqualTo(Level.INFO);
        assertThat(events.get(1).getFormattedMessage()).contains("[SqlExecutionTime]").contains("elapsedMillis=");
    }
}
