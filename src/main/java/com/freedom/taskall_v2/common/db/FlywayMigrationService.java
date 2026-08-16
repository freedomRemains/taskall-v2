package com.freedom.taskall_v2.common.db;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Flywayを使って、本番DB更新（issue #72）のマイグレーションを適用するクラスです。
 *
 * <p>
 * マイグレーションファイル（{@code V2__xxx.sql}等）は「db/flyway」配下（クラスパス）に配置します。
 * {@link DbInitializer}／{@link DbInitializationService}（初回ブートストラップ用フルCREATE）は
 * そのまま残し、本クラスはその後（{@link FlywayMigrationRunner}の{@code @Order(2)}）に実行します。
 * </p>
 *
 * <p>
 * 「TBL_DEF」が既に存在する既存DB（本番DB等、Flyway導入前の状態）と、この起動で
 * {@link DbInitializer}が新規作成した最新スキーマのDB（開発・テスト環境等）とで、
 * ベースライン化の考え方が異なるため、{@link DbBootstrapState}の状態に応じて処理を分岐します。
 * </p>
 *
 * <ul>
 * <li>既存DB（Flyway導入前の状態）: 「V1」としてベースライン化したうえで、{@code V2}以降の
 * 未適用マイグレーションを適用する({@code baselineOnMigrate=true}、{@code baselineVersion=1})。</li>
 * <li>この起動で新規作成した最新スキーマのDB: 「db/data」配下の最新資材を反映済みのため、
 * 発見できる最新バージョンとしてベースライン化する（マイグレーションの二重適用によるテーブル
 * 重複エラーを避けるため）。</li>
 * </ul>
 */
@Service
public class FlywayMigrationService {

    /** Flywayマイグレーションファイルの配置場所(クラスパス上) */
    static final String MIGRATION_LOCATION = "classpath:db/flyway";

    /** 「Flyway導入以前の状態」を表すベースラインバージョン */
    static final String LEGACY_BASELINE_VERSION = "1";

    private static final Logger logger = LoggerFactory.getLogger(FlywayMigrationService.class);

    private final DataSource dataSource;
    private final DbBootstrapState dbBootstrapState;

    public FlywayMigrationService(DataSource dataSource, DbBootstrapState dbBootstrapState) {
        this.dataSource = dataSource;
        this.dbBootstrapState = dbBootstrapState;
    }

    /**
     * Flywayマイグレーションを適用します。
     */
    public void migrate() {
        if (dbBootstrapState.isFreshlyBootstrapped()) {
            baselineFreshlyBootstrappedDatabase();
        } else {
            migrateExistingDatabase();
        }
    }

    private void baselineFreshlyBootstrappedDatabase() {

        // マイグレーション一覧から最新バージョンを特定する。1件も無い場合(Flyway導入直後で
        // まだマイグレーションファイルが追加されていない場合等)は、ベースライン化不要として
        // スキップする。
        Flyway probeFlyway = buildFlyway(LEGACY_BASELINE_VERSION).load();
        MigrationInfo[] allMigrations = probeFlyway.info().all();
        if (allMigrations.length == 0) {
            logger.info("Flywayマイグレーションファイルが存在しないため、ベースライン化をスキップします。");
            return;
        }

        // DbInitializerが今回の起動で最新スキーマを新規作成したため、その時点で存在する
        // 最新バージョンまで適用済みとしてベースライン化する(以降の起動でV2以降を
        // 二重実行し、テーブル重複エラーになることを防ぐ)。
        String latestVersion = allMigrations[allMigrations.length - 1].getVersion().getVersion();
        logger.info("新規作成したDBを、Flywayバージョン{}としてベースライン化します。", latestVersion);
        Flyway flyway = buildFlyway(latestVersion).load();
        flyway.baseline();
        flyway.migrate();
    }

    private void migrateExistingDatabase() {

        // 既存のDB(Flyway導入前の本番DB、または既にFlyway管理下のDB)は、未追跡の場合のみ
        // 「V1(Flyway導入時点の状態)」としてベースライン化したうえで、未適用のマイグレーションを適用する。
        // 既にflyway_schema_historyが存在する場合、baselineOnMigrateは何もしない。
        Flyway flyway = buildFlyway(LEGACY_BASELINE_VERSION).baselineOnMigrate(true).load();
        flyway.migrate();
    }

    private FluentConfiguration buildFlyway(String baselineVersion) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                // SQLiteは公式サポート対象ではなく、コミュニティDBサポートとして提供されるため有効化する
                // (将来のMySQL移行後は公式サポート対象のため、本フラグは影響しない)。
                .communityDBSupportEnabled(true)
                .baselineVersion(baselineVersion);
    }
}
