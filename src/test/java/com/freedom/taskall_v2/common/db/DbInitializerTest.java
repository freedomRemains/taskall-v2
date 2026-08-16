package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DbInitializerTest {

    @Mock
    private TblDefTableChecker tblDefTableChecker;

    @Mock
    private DbInitializationService dbInitializationService;

    @Mock
    private ApplicationArguments applicationArguments;

    private DbBootstrapState dbBootstrapState;

    private DbInitializer dbInitializer;

    @BeforeEach
    void setUp() {
        // DbBootstrapStateはMockitoの@InjectMocksでは注入されない単純なStateオブジェクトのため、
        // 実体を生成してコンストラクタへ明示的に渡す。
        dbBootstrapState = new DbBootstrapState();
        dbInitializer = new DbInitializer(tblDefTableChecker, dbInitializationService, dbBootstrapState);
    }

    @Test
    void TBL_DEFテーブルが存在しない場合は初期化処理が実行されDbBootstrapStateへ記録されること() {

        when(tblDefTableChecker.existsTblDefTable()).thenReturn(false);

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService).initializeDatabase();
        assertThat(dbBootstrapState.isFreshlyBootstrapped()).isTrue();
    }

    @Test
    void TBL_DEFテーブルが既に存在する場合は初期化処理が実行されずDbBootstrapStateも記録されないこと() {

        when(tblDefTableChecker.existsTblDefTable()).thenReturn(true);

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService, never()).initializeDatabase();
        assertThat(dbBootstrapState.isFreshlyBootstrapped()).isFalse();
    }
}

