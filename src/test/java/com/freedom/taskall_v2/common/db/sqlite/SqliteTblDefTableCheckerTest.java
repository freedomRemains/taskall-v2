package com.freedom.taskall_v2.common.db.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SqliteTblDefTableCheckerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SqliteTblDefTableChecker sqliteTblDefTableChecker;

    @Test
    void sqlite_master上にTBL_DEFテーブルが存在する場合はtrueが返却されること() {

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString())).thenReturn(List.of("TBL_DEF"));

        assertThat(sqliteTblDefTableChecker.existsTblDefTable()).isTrue();
    }

    @Test
    void sqlite_master上にTBL_DEFテーブルが存在しない場合はfalseが返却されること() {

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString())).thenReturn(List.of());

        assertThat(sqliteTblDefTableChecker.existsTblDefTable()).isFalse();
    }
}
