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

        // 28テーブル分のDROP・CREATE（各1ステートメント）と、INSERT分（合計1171ステートメント）が実行される
        // (issue #69でPASSWORD_RESETテーブルが追加されたため、24から25へ変更)
        // (issue #78でSIGN_UPテーブルが追加されたため、25から26へ変更)
        // (同issueでサインアップ画面・メッセージ・画面部品関連のDBデータが増え、INSERT総数が770から828へ増加)
        // (同issueのフォローアップでサインアップ完了通知用のDBデータが増え、828から831へ増加)
        // issue #80でreCAPTCHA関連のDBデータ3件が増え、831から834へ増加。
        // issue #84で案件一覧画面用にANKEN・ATTR_IN_ANKENテーブルが追加され、26から28へ変更。
        // 同issueでATTR_GRP/ATTR(既存テーブル定義のみで未使用だったもの)へのデータ投入、案件・
        // 案件内属性・画面関連のDBデータが増え、INSERT総数が834から1171へ増加。
        // issue #96でメールアドレス登録画面用にMAIL_ADDR_IN_ACCNTテーブルが追加され、28から29へ変更。
        // 同issueで画面・メッセージ・画面部品関連のDBデータが増え、INSERT総数が1171から1207へ増加。
        verify(jdbcTemplate, times(29 + 29 + 1207)).execute(anyString());
    }
}
