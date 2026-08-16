package com.freedom.taskall_v2.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時、Flywayによる本番DB更新（issue #72）のマイグレーションを実行するクラスです。
 *
 * <p>
 * {@code @Order(2)}により、必ず{@link DbInitializer}（{@code @Order(1)}）の後に実行されるようにする
 * (スキーマ・シードデータが存在しない状態でFlywayを実行してしまうことを避けるため)。また、
 * {@link DefaultAccountCredentialInitializer}（{@code @Order(3)}）より先に実行することで、
 * ACCNTテーブルの変更を含むマイグレーションが、認証情報差し替え処理より前に適用されるようにする。
 * </p>
 */
@Component
@Order(2)
public class FlywayMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final FlywayMigrationService flywayMigrationService;

    public FlywayMigrationRunner(FlywayMigrationService flywayMigrationService) {
        this.flywayMigrationService = flywayMigrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Flywayによる本番DB更新のマイグレーション適用を開始します。");
        flywayMigrationService.migrate();
    }
}
