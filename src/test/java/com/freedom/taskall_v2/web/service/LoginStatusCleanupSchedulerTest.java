package com.freedom.taskall_v2.web.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginStatusCleanupSchedulerTest {

    @Mock
    private LoginStatusService loginStatusService;

    @InjectMocks
    private LoginStatusCleanupScheduler loginStatusCleanupScheduler;

    @Test
    void 定期実行時にLoginStatusServiceのdeleteExpiredが呼び出されること() {

        when(loginStatusService.deleteExpired()).thenReturn(2);

        loginStatusCleanupScheduler.cleanupExpiredLoginStatus();

        verify(loginStatusService).deleteExpired();
    }
}
