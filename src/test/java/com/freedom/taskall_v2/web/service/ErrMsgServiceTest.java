package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import com.freedom.taskall_v2.common.db.RecordQueryService;

/**
 * {@link ErrMsgService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class ErrMsgServiceTest {

    private static final String GNR_VAL_SQL = "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY_VAL_ID = ?";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ErrMsgService errMsgService;

    @BeforeEach
    void setUp() {
        errMsgService = new ErrMsgService(recordQueryService, jdbcTemplate);
    }

    @Test
    void メッセージが存在する場合はERR_MSGへ登録され採番されたIDが返却されること() {

        LinkedHashMap<String, String> gnrValRow = new LinkedHashMap<>();
        gnrValRow.put("GNR_VAL", "メールアドレスもしくはパスワードが間違っています。");
        when(recordQueryService.select(eq(GNR_VAL_SQL), eq(List.of("1000401"))))
                .thenReturn(new ArrayList<>(List.of(gnrValRow)));

        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Map<String, Object> generatedKey = new LinkedHashMap<>();
            generatedKey.put("ERR_MSG_ID", 123);
            keyHolder.getKeyList().add(generatedKey);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        String errMsgKey = errMsgService.getErrMsgKey("session-1", "1000001", "1000401");

        assertThat(errMsgKey).isEqualTo("123");
    }

    @Test
    void 汎用キー値マスタにメッセージが存在しない場合は固定値0が返却されること() {

        when(recordQueryService.select(eq(GNR_VAL_SQL), eq(List.of("9999999"))))
                .thenReturn(new ArrayList<>());

        String errMsgKey = errMsgService.getErrMsgKey("session-1", "1000001", "9999999");

        assertThat(errMsgKey).isEqualTo("0");
    }
}
