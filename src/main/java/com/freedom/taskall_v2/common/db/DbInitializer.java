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
 * {@code @Order(1)}により、{@link com.freedom.taskall_v2.common.db.DefaultAccountCredentialInitializer}
 * （SSM Parameter Store経由のデフォルトアカウントパスワード差し替え、issue #41）より必ず先に
 * 実行されるようにする(初回起動時はテーブル自体が存在しないため、先にスキーマ・シードデータを
 * 作成しておく必要がある)。
 * </p>
 */
@Component
@Order(1)
public class DbInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DbInitializer.class);

    private final TblDefTableChecker tblDefTableChecker;
    private final DbInitializationService dbInitializationService;

    public DbInitializer(TblDefTableChecker tblDefTableChecker, DbInitializationService dbInitializationService) {
        this.tblDefTableChecker = tblDefTableChecker;
        this.dbInitializationService = dbInitializationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 既にTBL_DEFが存在する場合は初回起動ではないため、DB初期化を行わずに処理を終了します。
        if (tblDefTableChecker.existsTblDefTable()) {
            logger.info("TBL_DEFテーブルが既に存在するため、初期化処理をスキップします。");
            return;
        }

        // 初回起動時のみ、事前生成済みSQLを使って初期テーブルと初期データを投入します。
        logger.info("TBL_DEFテーブルが存在しないため、初期テーブル／データの作成を行います。");
        dbInitializationService.initializeDatabase();
    }
}
