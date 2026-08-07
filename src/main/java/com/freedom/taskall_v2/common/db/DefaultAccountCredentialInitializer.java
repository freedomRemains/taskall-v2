package com.freedom.taskall_v2.common.db;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * アプリ起動時、シードデータ(ACCNT.txt)由来のデフォルトアカウント全件のパスワード・
 * メールアドレスを、AWS SSM Parameter Store経由で取得した本番用の値へ差し替えるクラスです
 * (issue #41)。
 *
 * <p>
 * issue #39のレビューで、シードデータの全アカウント(guest/gnruser/cmpnyuser/master/
 * grandmaster)が同一のbcryptハッシュ(平文パスワード「password」)を共有しており、かつログイン後は
 * DBメンテナンス画面から任意のテーブルデータを操作できてしまうため、本番リリース前に必ず
 * 解消すべきセキュリティ上の課題として、本クラスを新設した。
 * </p>
 *
 * <p>
 * 平文パスワード・メールアドレスはSSM Parameter Store(SecureString)からアプリのメモリ上へ
 * 直接取得し、ディスクやS3等のファイルには一切書き出さない。ハッシュ化は移植元の
 * {@code DbUpdateBySqlFileService}のような外部SQLファイル実行方式ではなく、ログイン時の
 * 照合処理と同一の{@link PasswordEncoder}(bcrypt)Beanを用いることで、
 * 外部ツール(htpasswd等)による生成との方式差異・コストパラメータ不一致のリスクを排除する。
 * </p>
 *
 * <p>
 * パスワードの冪等性は「対象アカウントの現在のPASSWORDが、シードデータ由来の既知の
 * デフォルトハッシュと一致するかどうか」で、メールアドレスの冪等性は「現在のMAIL_ADDRESSが
 * シードデータ由来の既知のデフォルト値と一致するかどうか」で、それぞれ独立に判定する。
 * 既に変更済みの項目は上書きしないため、再起動のたびに管理者の変更が失われることはない。
 * </p>
 *
 * <p>
 * メールアドレスは、実際にメールを受信できる本物のアドレスを本番運用者がGitリポジトリに
 * 一切コミットせずに設定できるようにするための仕組みであり、パスワードと異なりSSMパラメータの
 * 設定は必須としない(未設定の場合はシードデータのメールアドレスのまま起動を継続する)。
 * </p>
 *
 * <p>
 * {@code taskall.credential-init.enabled=true}の環境(本番のEC2インスタンス)でのみ動作する。
 * パスワード用のSSMパラメータが未設定の場合は、既知のデフォルトパスワードのまま本番リリース
 * されることを防ぐため、起動自体を失敗させる({@link ApplicationInternalException}をスローする)。
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

    /** ACCNT_IDと、シードデータ(ACCNT.txt)由来の既知のデフォルトメールアドレスの対応 */
    private static final Map<String, String> DEFAULT_MAIL_ADDRESS_BY_ID = new LinkedHashMap<>();
    static {
        ACCOUNT_KEY_BY_ID.put("1000001", "guest");
        ACCOUNT_KEY_BY_ID.put("1000101", "individual");
        ACCOUNT_KEY_BY_ID.put("1000201", "corporate");
        ACCOUNT_KEY_BY_ID.put("1000301", "master");
        ACCOUNT_KEY_BY_ID.put("1000401", "grandmaster");

        DEFAULT_MAIL_ADDRESS_BY_ID.put("1000001", "guest@account.com");
        DEFAULT_MAIL_ADDRESS_BY_ID.put("1000101", "gnruser@account.com");
        DEFAULT_MAIL_ADDRESS_BY_ID.put("1000201", "cmpnyuser@account.com");
        DEFAULT_MAIL_ADDRESS_BY_ID.put("1000301", "master@account.com");
        DEFAULT_MAIL_ADDRESS_BY_ID.put("1000401", "grandmaster@account.com");
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
        logger.info("SSM Parameter Store経由のデフォルトアカウント認証情報差し替え処理を開始します。");
        for (Map.Entry<String, String> entry : ACCOUNT_KEY_BY_ID.entrySet()) {
            applyCredentialIfDefault(entry.getKey(), entry.getValue());
        }
        logger.info("SSM Parameter Store経由のデフォルトアカウント認証情報差し替え処理が完了しました。");
    }

    private void applyCredentialIfDefault(String accntId, String accountKey) {

        LinkedHashMap<String, String> currentRow = findCurrentAccount(accntId);
        boolean passwordNeedsUpdate = DEFAULT_PASSWORD_HASH.equals(currentRow.get("PASSWORD"));
        boolean mailAddressNeedsUpdate = DEFAULT_MAIL_ADDRESS_BY_ID.get(accntId).equals(currentRow.get("MAIL_ADDRESS"));

        if (!passwordNeedsUpdate && !mailAddressNeedsUpdate) {
            logger.info("ACCNT_ID={}は既にパスワード・メールアドレスとも変更済みのため、スキップします。", accntId);
            return;
        }

        // パスワードは既知のデフォルトのまま本番リリースされることを防ぐため、必須パラメータとして扱う
        String newPasswordHash = null;
        if (passwordNeedsUpdate) {
            String parameterName = properties.getParameterPrefix() + "/" + accountKey + "/password";
            String plainPassword = ssmParameterFetcher.fetchSecureString(parameterName)
                    .orElseThrow(() -> new ApplicationInternalException(
                            msg.get("msg.err.common.db.ssmCredentialParameterNotFound", parameterName)));
            newPasswordHash = passwordEncoder.encode(plainPassword);
        }

        // メールアドレスは実際に受信可能な本物のアドレスをGitへコミットせず設定するための任意項目であり、
        // パスワードと異なりSSM未設定でも起動を継続する(シードデータのメールアドレスのまま維持する)
        String newMailAddress = null;
        if (mailAddressNeedsUpdate) {
            String parameterName = properties.getParameterPrefix() + "/" + accountKey + "/mailAddress";
            Optional<String> mailAddress = ssmParameterFetcher.fetchSecureString(parameterName);
            if (mailAddress.isPresent()) {
                newMailAddress = mailAddress.get();
            } else {
                logger.info("ACCNT_ID={}のメールアドレス用SSMパラメータが未設定のため、シードデータのままとします。",
                        accntId);
            }
        }

        if (newPasswordHash == null && newMailAddress == null) {
            return;
        }

        updateAccount(accntId, newPasswordHash, newMailAddress);
        logger.info("ACCNT_ID={}の認証情報をSSM Parameter Store由来の値へ差し替えました。", accntId);
    }

    private LinkedHashMap<String, String> findCurrentAccount(String accntId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService
                .select("SELECT PASSWORD, MAIL_ADDRESS FROM ACCNT WHERE ACCNT_ID = ?", List.of(accntId));
        if (rows.isEmpty()) {
            // シードデータ投入前(DbInitializerより先に実行されてしまった等)の想定外の状態
            throw new ApplicationInternalException(
                    msg.get("msg.err.common.db.ssmCredentialAccountNotFound", accntId));
        }
        return rows.get(0);
    }

    private void updateAccount(String accntId, String newPasswordHash, String newMailAddress) {

        // 変更対象の項目のみをSET句へ組み込む(パスワード・メールアドレスは独立して更新有無を判定するため)
        StringBuilder assignments = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (newPasswordHash != null) {
            assignments.append("PASSWORD = ?, ");
            params.add(newPasswordHash);
        }
        if (newMailAddress != null) {
            assignments.append("MAIL_ADDRESS = ?, ");
            params.add(newMailAddress);
        }

        String dateString = LocalDateTime.now().format(DATE_FORMAT);
        params.add(UPDATED_BY);
        params.add(dateString);
        params.add(accntId);

        String sql = "UPDATE ACCNT SET " + assignments + "VERSION = VERSION + 1, UPDATED_BY = ?, UPDATED_AT = ? "
                + "WHERE ACCNT_ID = ?";
        jdbcTemplate.update(sql, params.toArray());
    }
}
