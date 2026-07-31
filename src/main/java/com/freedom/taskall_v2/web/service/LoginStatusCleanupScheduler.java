package com.freedom.taskall_v2.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 「ログイン試行(LOGIN_STATUS)」テーブルの期限切れ行を定期的に物理削除するスケジューラです。
 *
 * <p>
 * パスワード認証後にパスコードを一度も入力せず離脱した場合等、そのセッションが再度
 * アクセスしてこない限り行が残り続けてしまうため、10分間隔で有効期限切れの行を一括削除します
 * (設計書「『ログイン試行(LOGIN_STATUS)』の定期クリーンアップ」節)。
 * </p>
 */
@Component
public class LoginStatusCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LoginStatusCleanupScheduler.class);

    private final LoginStatusService loginStatusService;

    public LoginStatusCleanupScheduler(LoginStatusService loginStatusService) {
        this.loginStatusService = loginStatusService;
    }

    /** 10分間隔で、有効期限切れのLOGIN_STATUS行を一括削除する。 */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredLoginStatus() {
        int deletedCount = loginStatusService.deleteExpired();
        logger.info("期限切れのLOGIN_STATUS行を削除しました。deletedCount={}", deletedCount);
    }
}
