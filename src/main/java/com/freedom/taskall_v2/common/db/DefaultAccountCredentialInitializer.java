package com.freedom.taskall_v2.common.db;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.freedom.taskall_v2.common.aws.SsmParameterFetcher;
import com.freedom.taskall_v2.common.config.CredentialInitProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * アプリ起動時、シードデータ(ACCNT.txt)由来のデフォルトアカウント全件のパスワードを、
 * AWS SSM Parameter Store経由で取得した本番用の値へ差し替えるクラスです(issue #41)。
 *
 * <p>
 * issue #39のレビューで、シードデータの全アカウント(guest/gnruser/cmpnyuser/master/
 * grandmaster)が同一のbcryptハッシュ(平文パスワード「password」)を共有しており、かつログイン後は
 * DBメンテナンス画面から任意のテーブルデータを操作できてしまうため、本番リリース前に必ず
 * 解消すべきセキュリティ上の課題として、本クラスを新設した。
 * </p>
 *
 * <p>
 * 平文パスワードはSSM Parameter Store(SecureString)からアプリのメモリ上へ直接取得し、
 * ディスクやS3等のファイルには一切書き出さない。ハッシュ化は移植元の
 * {@code DbUpdateBySqlFileService}のような外部SQLファイル実行方式ではなく、ログイン時の
 * 照合処理と同一の{@link PasswordEncoder}(bcrypt)Beanを用いることで、
 * 外部ツール(htpasswd等)による生成との方式差異・コストパラメータ不一致のリスクを排除する。
 * </p>
 *
 * <p>
 * 冪等性は「対象アカウントの現在のPASSWORDが、シードデータ由来の既知のデフォルトハッシュと
 * 一致するかどうか」で判定する。既に本処理やアプリの通常機能でパスワードが変更済みの場合は
 * 上書きしないため、再起動のたびに管理者の変更が失われることはない。
 * </p>
 *
 * <p>
 * {@code taskall.credential-init.enabled=true}の環境(本番のEC2インスタンス)でのみ動作する。
 * SSMパラメータが未設定の場合は、既知のデフォルトパスワードのまま本番リリースされることを
 * 防ぐため、起動自体を失敗させる({@link ApplicationInternalException}をスローする)。
 * </p>
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "taskall.credential-init", name = "enabled", havingValue = "true")
public class DefaultAccountCredentialInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAccountCredentialInitializer.class);

    /** シードデータ(ACCNT.txt)の全アカウントに共通する、初期パスワード「password」のbcryptハッシュ */
    static final String DEFAULT_PASSWORD_HASH = "$2a$10$w6D8P5pBpmrfJK0c2tKKre9e39qKzUuSwB8WdNyNUHkP8WXacC0fK";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 更新者(UPDATED_BY)として記録する識別子。データロード由来ではなく本処理由来と分かるようにする */
    private static final String UPDATED_BY = "ssm_credential_init";

    /** ACCNT_IDと、SSMパラメータ名のサフィックス(アカウント種別キー)の対応(ACCNT.txt記載順) */
    private static final Map<String, String> ACCOUNT_KEY_BY_ID = new LinkedHashMap<>();
    static {
        ACCOUNT_KEY_BY_ID.put("1000001", "guest");
        ACCOUNT_KEY_BY_ID.put("1000101", "individual");
        ACCOUNT_KEY_BY_ID.put("1000201", "corporate");
        ACCOUNT_KEY_BY_ID.put("1000301", "master");
        ACCOUNT_KEY_BY_ID.put("1000401", "grandmaster");
    }

    private final SsmParameterFetcher ssmParameterFetcher;
    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final CredentialInitProperties properties;
    private final MsgUtil msg;

    public DefaultAccountCredentialInitializer(SsmParameterFetcher ssmParameterFetcher,
            RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            CredentialInitProperties properties, MsgUtil msg) {
        this.ssmParameterFetcher = ssmParameterFetcher;
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.msg = msg;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        logger.info("SSM Parameter Store経由のデフォルトアカウントパスワード差し替え処理を開始します。");
        for (Map.Entry<String, String> entry : ACCOUNT_KEY_BY_ID.entrySet()) {
            applyCredentialIfDefault(entry.getKey(), entry.getValue());
        }
        logger.info("SSM Parameter Store経由のデフォルトアカウントパスワード差し替え処理が完了しました。");
    }

    private void applyCredentialIfDefault(String accntId, String accountKey) {

        // 既にデフォルトパスワードから変更済みの場合は、管理者による変更等を上書きしないためスキップする
        String currentPasswordHash = findCurrentPasswordHash(accntId);
        if (!DEFAULT_PASSWORD_HASH.equals(currentPasswordHash)) {
            logger.info("ACCNT_ID={}は既にデフォルトパスワードから変更済みのため、スキップします。", accntId);
            return;
        }

        // SSMパラメータが未設定のまま本番リリースされることを防ぐため、取得できない場合は起動自体を失敗させる
        String parameterName = properties.getParameterPrefix() + "/" + accountKey + "/password";
        String plainPassword = ssmParameterFetcher.fetchSecureString(parameterName)
                .orElseThrow(() -> new ApplicationInternalException(
                        msg.get("msg.err.common.db.ssmCredentialParameterNotFound", parameterName)));

        String newPasswordHash = passwordEncoder.encode(plainPassword);
        updatePassword(accntId, newPasswordHash);
        logger.info("ACCNT_ID={}のパスワードをSSM Parameter Store由来の値へ差し替えました。", accntId);
    }

    private String findCurrentPasswordHash(String accntId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService
                .select("SELECT PASSWORD FROM ACCNT WHERE ACCNT_ID = ?", List.of(accntId));
        if (rows.isEmpty()) {
            // シードデータ投入前(DbInitializerより先に実行されてしまった等)の想定外の状態
            throw new ApplicationInternalException(
                    msg.get("msg.err.common.db.ssmCredentialAccountNotFound", accntId));
        }
        return rows.get(0).get("PASSWORD");
    }

    private void updatePassword(String accntId, String newPasswordHash) {
        String dateString = LocalDateTime.now().format(DATE_FORMAT);
        jdbcTemplate.update(
                "UPDATE ACCNT SET PASSWORD = ?, VERSION = VERSION + 1, UPDATED_BY = ?, UPDATED_AT = ? "
                        + "WHERE ACCNT_ID = ?",
                newPasswordHash, UPDATED_BY, dateString, accntId);
    }
}
