package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class LiveTableColumnDefLoaderTest {

    private static final String COLUMN_DEF_SQL = """
            SELECT *
            FROM TBL_DEF
            WHERE TABLE_NAME = ?
            ORDER BY TBL_DEF_ID
            """;

    @Mock
    private RecordQueryService recordQueryService;

    private LiveTableColumnDefLoader liveTableColumnDefLoader;

    @BeforeEach
    void setUp() {
        liveTableColumnDefLoader = new LiveTableColumnDefLoader(recordQueryService, new MsgUtil());
    }

    @Test
    void 指定したテーブル名のカラム定義をTBL_DEF_ID順で取得できること() {

        LinkedHashMap<String, String> idRow = new LinkedHashMap<>();
        idRow.put("TBL_DEF_ID", "1000001");
        idRow.put("TABLE_NAME", "ACCNT");
        idRow.put("FIELD_NAME", "ACCNT_ID");

        LinkedHashMap<String, String> nameRow = new LinkedHashMap<>();
        nameRow.put("TBL_DEF_ID", "1000002");
        nameRow.put("TABLE_NAME", "ACCNT");
        nameRow.put("FIELD_NAME", "ACCOUNT_NAME");

        when(recordQueryService.select(eq(COLUMN_DEF_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(List.of(idRow, nameRow)));

        List<Map<String, String>> result = liveTableColumnDefLoader.load("ACCNT");

        assertThat(result).containsExactly(idRow, nameRow);
        verify(recordQueryService).select(eq(COLUMN_DEF_SQL), eq(List.of("ACCNT")));
    }

    @Test
    void ライブDBにテーブル定義が存在しない場合は例外がスローされること() {

        when(recordQueryService.select(eq(COLUMN_DEF_SQL), eq(List.of("NOT_EXIST"))))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> liveTableColumnDefLoader.load("NOT_EXIST"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
