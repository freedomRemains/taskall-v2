package com.freedom.taskall_v2.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 「パスワード再設定(PASSWORD_RESET)」テーブルの期限切れ行を定期削除するスケジューラです。
 */
@Component
public class PasswordResetCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetCleanupScheduler.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetCleanupScheduler(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * DB初期化との競合を避けるためinitialDelay付きで、10分ごとに期限切れ行を削除します。
     */
    @Scheduled(initialDelay = 10 * 60 * 1000, fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredPasswordReset() {
        int deletedCount = passwordResetService.deleteExpired();
        logger.info("期限切れのPASSWORD_RESET行を削除しました。deletedCount={}", deletedCount);
    }
}
