package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 「パスワード再設定(PASSWORD_RESET)」テーブルと、関連するアカウント更新を扱うサービスです。
 */
@Service
public class PasswordResetService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_FAIL_COUNT = 5;

    private static final String FIND_BY_ID_SQL = """
            SELECT PASSWORD_RESET_ID, SESSION_ID, MAIL_ADDRESS, AFTER_PASSWORD_HASH, PASSCODE_HASH,
                   FAIL_CNT, IS_LOCKED, EXPIRES_AT
            FROM PASSWORD_RESET
            WHERE PASSWORD_RESET_ID = ?
            """;

    private static final String FIND_BY_MAIL_SQL = """
            SELECT PASSWORD_RESET_ID, SESSION_ID, MAIL_ADDRESS, AFTER_PASSWORD_HASH, PASSCODE_HASH,
                   FAIL_CNT, IS_LOCKED, EXPIRES_AT
            FROM PASSWORD_RESET
            WHERE MAIL_ADDRESS = ?
            """;

    private static final String FIND_BY_SESSION_AND_MAIL_SQL = """
            SELECT PASSWORD_RESET_ID
            FROM PASSWORD_RESET
            WHERE SESSION_ID = ? AND MAIL_ADDRESS = ?
            """;

    private static final String FIND_ACCOUNT_BY_MAIL_SQL = """
            SELECT ACCNT_ID, MAIL_ADDRESS
            FROM ACCNT
            WHERE MAIL_ADDRESS = ?
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final MsgUtil msg;

    public PasswordResetService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
        this.msg = msg;
    }

    /**
     * 指定メールアドレスに紐づくパスワード再設定行を全件取得します。
     */
    public List<LinkedHashMap<String, String>> findByMailAddress(String mailAddress) {
        return recordQueryService.select(FIND_BY_MAIL_SQL, List.of(mailAddress));
    }

    /**
     * パスワード再設定IDで1行取得します。
     */
    public Optional<LinkedHashMap<String, String>> findById(String passwordResetId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_BY_ID_SQL, List.of(passwordResetId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * パスワード再設定行を新規作成し、採番されたIDを返します。
     */
    public String create(String sessionId, String mailAddress, String afterPasswordHash, String passcodeHash) {

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        String expiresAt = LocalDateTime.now().plusMinutes(5).format(DATE_FORMAT);

        jdbcTemplate.update("""
                INSERT INTO PASSWORD_RESET
                    (SESSION_ID, MAIL_ADDRESS, AFTER_PASSWORD_HASH, PASSCODE_HASH, FAIL_CNT, IS_LOCKED,
                     EXPIRES_AT, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                VALUES (?, ?, ?, ?, 0, 0, ?, 1, 0, ?, ?, ?, ?)
                """, sessionId, mailAddress, afterPasswordHash, passcodeHash, expiresAt,
                sessionId, currentDate, sessionId, currentDate);

        List<LinkedHashMap<String, String>> rows =
                recordQueryService.select(FIND_BY_SESSION_AND_MAIL_SQL, List.of(sessionId, mailAddress));
        if (rows.isEmpty()) {
            throw new ApplicationInternalException(
                    msg.get("msg.err.web.passwordReset.rowReloadFailed", sessionId, mailAddress));
        }
        return rows.get(0).get("PASSWORD_RESET_ID");
    }

    /**
     * 失敗回数を1回だけ加算し、5回到達時は同じUPDATE文内でロック状態へ遷移させます。
     */
    public boolean recordFailureAndLockIfNeeded(String passwordResetId, int currentFailCnt) {

        String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
        jdbcTemplate.update("""
                UPDATE PASSWORD_RESET
                SET FAIL_CNT = FAIL_CNT + 1,
                    IS_LOCKED = CASE WHEN FAIL_CNT + 1 >= ? THEN 1 ELSE IS_LOCKED END,
                    EXPIRES_AT = CASE WHEN FAIL_CNT + 1 >= ? THEN ? ELSE EXPIRES_AT END
                WHERE PASSWORD_RESET_ID = ?
                """, MAX_FAIL_COUNT, MAX_FAIL_COUNT, lockedUntil, passwordResetId);

        return currentFailCnt + 1 >= MAX_FAIL_COUNT;
    }

    /**
     * 6桁コードの有効期限切れをロック状態へ切り替え、15分後まで再試行不可とします。
     */
    public void lock(String passwordResetId) {
        String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
        jdbcTemplate.update("UPDATE PASSWORD_RESET SET IS_LOCKED = 1, EXPIRES_AT = ? WHERE PASSWORD_RESET_ID = ?",
                lockedUntil, passwordResetId);
    }

    /**
     * 対象行を物理削除します。
     */
    public void deleteById(String passwordResetId) {
        jdbcTemplate.update("DELETE FROM PASSWORD_RESET WHERE PASSWORD_RESET_ID = ?", passwordResetId);
    }

    /**
     * 指定メールアドレスに紐づく行を全件物理削除します。
     */
    public void deleteByMailAddress(String mailAddress) {
        jdbcTemplate.update("DELETE FROM PASSWORD_RESET WHERE MAIL_ADDRESS = ?", mailAddress);
    }

    /**
     * 期限切れ行を定期クリーンアップ対象として一括削除します。
     */
    public int deleteExpired() {
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        return jdbcTemplate.update("DELETE FROM PASSWORD_RESET WHERE EXPIRES_AT < ?", currentDate);
    }

    /**
     * メールアドレスからアカウント行を一意に検索します。
     */
    public Optional<LinkedHashMap<String, String>> findAccountByMailAddress(String mailAddress) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_ACCOUNT_BY_MAIL_SQL, List.of(mailAddress));
        return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    /**
     * アカウントのパスワードハッシュを更新します。
     */
    public void updateAccountPassword(String accountId, String afterPasswordHash) {

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        jdbcTemplate.update("""
                UPDATE ACCNT
                SET PASSWORD = ?, VERSION = VERSION + 1, UPDATED_BY = ?, UPDATED_AT = ?
                WHERE ACCNT_ID = ?
                """, afterPasswordHash, accountId, currentDate, accountId);
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
