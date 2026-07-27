package com.freedom.taskall_v2.common.db;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private DbInitializer dbInitializer;

    @Test
    void TBL_DEFテーブルが存在しない場合は初期化処理が実行されること() {

        when(tblDefTableChecker.existsTblDefTable()).thenReturn(false);

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService).initializeDatabase();
    }

    @Test
    void TBL_DEFテーブルが既に存在する場合は初期化処理が実行されないこと() {

        when(tblDefTableChecker.existsTblDefTable()).thenReturn(true);

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService, never()).initializeDatabase();
    }
}

