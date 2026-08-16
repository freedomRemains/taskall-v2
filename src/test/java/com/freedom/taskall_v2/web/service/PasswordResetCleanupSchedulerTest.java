package com.freedom.taskall_v2.web.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetCleanupSchedulerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetCleanupScheduler passwordResetCleanupScheduler;

    @Test
    void 定期実行時にPasswordResetServiceのdeleteExpiredが呼び出されること() {

        when(passwordResetService.deleteExpired()).thenReturn(3);

        passwordResetCleanupScheduler.cleanupExpiredPasswordReset();

        verify(passwordResetService).deleteExpired();
    }
}
