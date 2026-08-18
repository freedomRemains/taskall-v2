package com.freedom.taskall_v2.web.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 「サインアップ(SIGN_UP)」テーブルと、関連するアカウント作成を扱うサービスです。
 */
@Service
public class SignUpService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_FAIL_COUNT = 5;
    private static final String SIGN_UP_CREATOR = "sign_up";

    private static final String FIND_BY_ID_SQL = """
            SELECT SIGN_UP_ID, APROLE_ID, SESSION_ID, MAIL_ADDRESS, ACCOUNT_NAME, PASSWORD_HASH, PASSCODE_HASH,
                   FAIL_CNT, IS_LOCKED, EXPIRES_AT
            FROM SIGN_UP
            WHERE SIGN_UP_ID = ?
            """;

    private static final String FIND_BY_MAIL_SQL = """
            SELECT SIGN_UP_ID, APROLE_ID, SESSION_ID, MAIL_ADDRESS, ACCOUNT_NAME, PASSWORD_HASH, PASSCODE_HASH,
                   FAIL_CNT, IS_LOCKED, EXPIRES_AT
            FROM SIGN_UP
            WHERE MAIL_ADDRESS = ?
            """;

    private static final String FIND_BY_SESSION_AND_MAIL_SQL = """
            SELECT SIGN_UP_ID
            FROM SIGN_UP
            WHERE SESSION_ID = ? AND MAIL_ADDRESS = ?
            """;

    private static final String FIND_ACCOUNT_BY_MAIL_SQL = """
            SELECT ACCNT_ID, MAIL_ADDRESS
            FROM ACCNT
            WHERE LOWER(MAIL_ADDRESS) = LOWER(?)
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final MsgUtil msg;

    public SignUpService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
        this.msg = msg;
    }

    /**
     * 指定メールアドレスに紐づくサインアップ行を全件取得します。
     */
    public List<LinkedHashMap<String, String>> findByMailAddress(String mailAddress) {
        return recordQueryService.select(FIND_BY_MAIL_SQL, List.of(mailAddress));
    }

    /**
     * サインアップIDで1行取得します。
     */
    public Optional<LinkedHashMap<String, String>> findById(String signUpId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_BY_ID_SQL, List.of(signUpId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * サインアップ行を新規作成し、採番されたIDを返します。
     */
    public String create(String sessionId, String aproleId, String mailAddress, String accountName,
            String passwordHash, String passcodeHash) {

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        String expiresAt = LocalDateTime.now().plusMinutes(5).format(DATE_FORMAT);

        jdbcTemplate.update("""
                INSERT INTO SIGN_UP
                    (APROLE_ID, SESSION_ID, MAIL_ADDRESS, ACCOUNT_NAME, PASSWORD_HASH, PASSCODE_HASH, FAIL_CNT,
                     IS_LOCKED, EXPIRES_AT, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, 1, 0, ?, ?, ?, ?)
                """, aproleId, sessionId, mailAddress, accountName, passwordHash, passcodeHash, expiresAt,
                sessionId, currentDate, sessionId, currentDate);

        List<LinkedHashMap<String, String>> rows =
                recordQueryService.select(FIND_BY_SESSION_AND_MAIL_SQL, List.of(sessionId, mailAddress));
        if (rows.isEmpty()) {
            throw new ApplicationInternalException(
                    msg.get("msg.err.web.signUp.rowReloadFailed", sessionId, mailAddress));
        }
        return rows.get(0).get("SIGN_UP_ID");
    }

    /**
     * 失敗回数を1回だけ加算し、5回到達時は同じUPDATE文内でロック状態へ遷移させます。
     */
    public boolean recordFailureAndLockIfNeeded(String signUpId, int currentFailCnt) {

        String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
        jdbcTemplate.update("""
                UPDATE SIGN_UP
                SET FAIL_CNT = FAIL_CNT + 1,
                    IS_LOCKED = CASE WHEN FAIL_CNT + 1 >= ? THEN 1 ELSE IS_LOCKED END,
                    EXPIRES_AT = CASE WHEN FAIL_CNT + 1 >= ? THEN ? ELSE EXPIRES_AT END
                WHERE SIGN_UP_ID = ?
                """, MAX_FAIL_COUNT, MAX_FAIL_COUNT, lockedUntil, signUpId);

        return currentFailCnt + 1 >= MAX_FAIL_COUNT;
    }

    /**
     * 6桁コードの有効期限切れをロック状態へ切り替え、15分後まで再試行不可とします。
     */
    public void lock(String signUpId) {
        String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
        jdbcTemplate.update("UPDATE SIGN_UP SET IS_LOCKED = 1, EXPIRES_AT = ? WHERE SIGN_UP_ID = ?",
                lockedUntil, signUpId);
    }

    /**
     * 対象行を物理削除します。
     */
    public void deleteById(String signUpId) {
        jdbcTemplate.update("DELETE FROM SIGN_UP WHERE SIGN_UP_ID = ?", signUpId);
    }

    /**
     * 指定メールアドレスに紐づく行を全件物理削除します。
     */
    public void deleteByMailAddress(String mailAddress) {
        jdbcTemplate.update("DELETE FROM SIGN_UP WHERE MAIL_ADDRESS = ?", mailAddress);
    }

    /**
     * 期限切れ行を定期クリーンアップ対象として一括削除します。
     */
    public int deleteExpired() {
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        return jdbcTemplate.update("DELETE FROM SIGN_UP WHERE EXPIRES_AT < ?", currentDate);
    }

    /**
     * メールアドレス(大文字小文字を区別しない)からアカウント行を一意に検索します。
     */
    public Optional<LinkedHashMap<String, String>> findAccountByMailAddress(String mailAddress) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_ACCOUNT_BY_MAIL_SQL, List.of(mailAddress));
        return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    /**
     * 新規アカウント(ACCNT)と、対応する所属ロール(APROLE_IN_ACCNT)を1組作成します。
     */
    public void createAccount(String accountName, String mailAddress, String passwordHash, String aproleId) {

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);

        // ACCNTを登録し、AUTO_INCREMENTで採番されたACCNT_IDを控える
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ACCNT
                        (ACCOUNT_NAME, MAIL_ADDRESS, PASSWORD, VERSION, IS_DELETED, CREATED_BY, CREATED_AT,
                         UPDATED_BY, UPDATED_AT)
                    VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, accountName);
            ps.setString(2, mailAddress);
            ps.setString(3, passwordHash);
            ps.setString(4, SIGN_UP_CREATOR);
            ps.setString(5, currentDate);
            ps.setString(6, SIGN_UP_CREATOR);
            ps.setString(7, currentDate);
            return ps;
        }, keyHolder);
        String accountId = String.valueOf(keyHolder.getKey().longValue());

        // 採番されたACCNT_IDと、画面選択(個人/法人)に応じたAPROLE_IDでロールを紐づける
        jdbcTemplate.update("""
                INSERT INTO APROLE_IN_ACCNT
                    (ACCNT_ID, APROLE_ID, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                VALUES (?, ?, 1, 0, ?, ?, ?, ?)
                """, accountId, aproleId, SIGN_UP_CREATOR, currentDate, SIGN_UP_CREATOR, currentDate);
    }

    /**
     * 行がロック中かどうかを判定します。
     */
    public boolean isLocked(LinkedHashMap<String, String> row) {
        return "1".equals(row.get("IS_LOCKED"));
    }

    /**
     * 行の有効期限が切れているかどうかを判定します。
     */
    public boolean isExpired(LinkedHashMap<String, String> row) {
        LocalDateTime expiresAt = LocalDateTime.parse(row.get("EXPIRES_AT"), DATE_FORMAT);
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
