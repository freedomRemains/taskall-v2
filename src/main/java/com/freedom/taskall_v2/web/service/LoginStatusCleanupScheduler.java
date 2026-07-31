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

    /**
     * 10分間隔で、有効期限切れのLOGIN_STATUS行を一括削除する。
     *
     * <p>
     * {@code initialDelay}を設けず起動直後に実行すると、DB未初期化状態
     * （{@code DbInitializer}によるテーブル作成が完了する前）のアプリ起動時に
     * 本処理が先に走り、LOGIN_STATUSテーブル不在エラーになる競合が発生し得るため、
     * 初回実行も{@code fixedRate}と同じ間隔だけ遅延させる。
     * </p>
     */
    @Scheduled(initialDelay = 10 * 60 * 1000, fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredLoginStatus() {
        int deletedCount = loginStatusService.deleteExpired();
        logger.info("期限切れのLOGIN_STATUS行を削除しました。deletedCount={}", deletedCount);
    }
}
