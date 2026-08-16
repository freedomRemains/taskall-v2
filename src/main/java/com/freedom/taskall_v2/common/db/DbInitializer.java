package com.freedom.taskall_v2.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時、「TBL_DEF」テーブルが存在しなければ、事前生成済みのSQL
 * （{@code classpath:db/sql/*.sql}）を使ってDROP／CREATE／INSERTを実行するクラスです。
 *
 * <p>
 * 「TBL_DEF」テーブルの存在確認方法はDB製品ごとに異なる(例: SQLiteの{@code sqlite_master})ため、
 * 本クラス自体はDB製品に依存せず、{@link TblDefTableChecker}の実装（DB製品ごとに
 * {@code common/db/[個別データベース名]}パッケージへ配置）をコンストラクタで注入して使用します。
 * </p>
 *
 * <p>
 * 生成済みのSQLファイルはクラスパス上のリソースとして読み込むため、開発時に限らず、
 * 起動可能jarにパッケージされた状態でも動作します。SQLファイル自体は
 * {@link DbSchemaSqlGenerator} により「db/data」配下の資材から生成し、
 * 「db/sql」配下に配置しておくものとします。
 * </p>
 *
 * <p>
 * {@code @Order(1)}により、{@link com.freedom.taskall_v2.common.db.FlywayMigrationRunner}
 * （本番DB更新の仕組み、issue #72、{@code @Order(2)}）・{@link DefaultAccountCredentialInitializer}
 * （SSM Parameter Store経由のデフォルトアカウントパスワード差し替え、issue #41、{@code @Order(3)}）
 * より必ず先に実行されるようにする(初回起動時はテーブル自体が存在しないため、先にスキーマ・
 * シードデータを作成しておく必要がある)。
 * </p>
 */
@Component
@Order(1)
public class DbInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DbInitializer.class);

    private final TblDefTableChecker tblDefTableChecker;
    private final DbInitializationService dbInitializationService;
    private final DbBootstrapState dbBootstrapState;

    public DbInitializer(TblDefTableChecker tblDefTableChecker, DbInitializationService dbInitializationService,
            DbBootstrapState dbBootstrapState) {
        this.tblDefTableChecker = tblDefTableChecker;
        this.dbInitializationService = dbInitializationService;
        this.dbBootstrapState = dbBootstrapState;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 既にTBL_DEFが存在する場合は初回起動ではないため、DB初期化を行わずに処理を終了します。
        if (tblDefTableChecker.existsTblDefTable()) {
            logger.info("TBL_DEFテーブルが既に存在するため、初期化処理をスキップします。");
            return;
        }

        // 初回起動時のみ、事前生成済みSQLを使って初期テーブルと初期データを投入します。
        // 「今回の起動で新規作成した」ことを、後続のFlywayMigrationRunner(issue #72)へ
        // 伝えるため、DbBootstrapStateへ記録しておく。
        logger.info("TBL_DEFテーブルが存在しないため、初期テーブル／データの作成を行います。");
        dbInitializationService.initializeDatabase();
        dbBootstrapState.setFreshlyBootstrapped(true);
    }
}
