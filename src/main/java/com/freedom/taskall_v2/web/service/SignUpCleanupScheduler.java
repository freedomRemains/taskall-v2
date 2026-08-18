package com.freedom.taskall_v2.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 「サインアップ(SIGN_UP)」テーブルの期限切れ行を定期削除するスケジューラです。
 */
@Component
public class SignUpCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SignUpCleanupScheduler.class);

    private final SignUpService signUpService;

    public SignUpCleanupScheduler(SignUpService signUpService) {
        this.signUpService = signUpService;
    }

    /**
     * DB初期化との競合を避けるためinitialDelay付きで、10分ごとに期限切れ行を削除します。
     */
    @Scheduled(initialDelay = 10 * 60 * 1000, fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredSignUp() {
        int deletedCount = signUpService.deleteExpired();
        logger.info("期限切れのSIGN_UP行を削除しました。deletedCount={}", deletedCount);
    }
}
