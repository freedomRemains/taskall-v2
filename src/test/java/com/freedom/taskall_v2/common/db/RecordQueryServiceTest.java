package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link RecordQueryService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class RecordQueryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RecordQueryService recordQueryService;

    @BeforeEach
    void setUp() {
        recordQueryService = new RecordQueryService(jdbcTemplate);
    }

    @Test
    void バインドパラメータ無しのSELECT結果が文字列マップのリストとして返却されること() {

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", 1000001);
        row.put("ACCOUNT_NAME", "ゲスト");
        when(jdbcTemplate.queryForList(eq("SELECT * FROM ACCNT"), any(Object[].class)))
                .thenReturn(List.of(row));

        var result = recordQueryService.select("SELECT * FROM ACCNT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("ACCNT_ID")).isEqualTo("1000001");
        assertThat(result.get(0).get("ACCOUNT_NAME")).isEqualTo("ゲスト");
    }

    @Test
    void バインドパラメータ付きのSELECTが実行されること() {

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", 1000001);
        when(jdbcTemplate.queryForList(eq("SELECT * FROM ACCNT WHERE ACCNT_ID = ?"), eq("1000001")))
                .thenReturn(List.of(row));

        var result = recordQueryService.select("SELECT * FROM ACCNT WHERE ACCNT_ID = ?", List.of("1000001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("ACCNT_ID")).isEqualTo("1000001");
        verify(jdbcTemplate).queryForList("SELECT * FROM ACCNT WHERE ACCNT_ID = ?", "1000001");
    }

    @Test
    void nullカラム値がnullのまま返却されること() {

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("APROLE_ID", null);
        when(jdbcTemplate.queryForList(eq("SELECT APROLE_ID FROM REQUIRE_APROLE"), any(Object[].class)))
                .thenReturn(List.of(row));

        var result = recordQueryService.select("SELECT APROLE_ID FROM REQUIRE_APROLE");

        assertThat(result.get(0)).containsEntry("APROLE_ID", null);
    }
}
