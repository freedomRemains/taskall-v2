package com.freedom.taskall_v2.common.db;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link DbInitializationService}のテストです。
 *
 * <p>
 * 実際のクラスパス上の資材（{@code db/data/TBL_DEF.txt}、{@code db/sql/*.sql}）を使って、
 * SQL実行対象の件数分、{@link JdbcTemplate#execute(String)}が呼ばれることを検証します。
 * {@link TableDefLoader}はテスト対象と協働する実装クラスのため、実物をそのまま使用します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DbInitializationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DbInitializationService dbInitializationService;

    @BeforeEach
    void setUp() {
        dbInitializationService = new DbInitializationService(jdbcTemplate);
    }

    @Test
    void 全テーブル分のDROP_CREATE_INSERT文が実行されること() {

        dbInitializationService.initializeDatabase();

        // 22テーブル分のDROP・CREATEと、データファイルが存在するテーブル分(673レコード)のINSERTが実行される
        // (テーブル一覧の定義参照/データ編集リンクをURI_PATTERN経由に統一したため、URI_PATTERNが2件増え671から673へ変更)
        verify(jdbcTemplate, times(22 + 22 + 673)).execute(anyString());
    }
}
