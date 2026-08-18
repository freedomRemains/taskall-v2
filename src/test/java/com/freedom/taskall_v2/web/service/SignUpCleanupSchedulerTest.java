package com.freedom.taskall_v2.web.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignUpCleanupSchedulerTest {

    @Mock
    private SignUpService signUpService;

    @InjectMocks
    private SignUpCleanupScheduler signUpCleanupScheduler;

    @Test
    void 定期実行時にSignUpServiceのdeleteExpiredが呼び出されること() {

        when(signUpService.deleteExpired()).thenReturn(3);

        signUpCleanupScheduler.cleanupExpiredSignUp();

        verify(signUpService).deleteExpired();
    }
}
