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

        // 25テーブル分のDROP・CREATE（各1ステートメント）と、INSERT分（合計770ステートメント）が実行される
        // (issue #69でPASSWORD_RESETテーブルが追加されたため、24から25へ変更)
        // (同issueでパスワード再設定画面・メッセージ・画面部品関連のDBデータが増え、INSERT総数が713から770へ増加)
        verify(jdbcTemplate, times(25 + 25 + 770)).execute(anyString());
    }
}
