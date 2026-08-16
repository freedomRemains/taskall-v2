package com.freedom.taskall_v2.common.db;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class FlywayMigrationRunnerTest {

    @Mock
    private FlywayMigrationService flywayMigrationService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private FlywayMigrationRunner flywayMigrationRunner;

    @Test
    void FlywayMigrationServiceのmigrateメソッドを呼び出すこと() {

        flywayMigrationRunner.run(applicationArguments);

        verify(flywayMigrationService).migrate();
    }
}
