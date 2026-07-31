package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * 「アカウント認証ロック(ACCNT_AUTH_LOCK)」テーブルの読み書きを行うサービスです。
 *
 * <p>
 * アカウント単位(セッションをまたいだ合算)で認証失敗回数とロック状態を管理します。
 * どのセッションからの失敗であっても、この1行に対して合算することでブルートフォース対策の
 * 集計をアカウント全体で行います。
 * </p>
 */
@Service
public class AccntAuthLockService {

    private static final Logger logger = LoggerFactory.getLogger(AccntAuthLockService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MAX_FAIL_COUNT = 5;

    private static final String FIND_SQL = "SELECT ACCNT_AUTH_LOCK_ID, FAIL_CNT, LOCKED_UNTIL "
            + "FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final MsgUtil msg;

    public AccntAuthLockService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
        this.msg = msg;
    }

    /**
     * アカウントがロック中かどうかを判定します。ロック解除予定時刻が現在時刻より未来の場合のみ
     * ロック中とします。自然失効(過去のロック解除予定時刻が残存している状態)を検知した場合は、
     * 次回の誤入力で即座に再ロックされることを防ぐため、失敗回数を0へリセットします。
     */
    public boolean isLocked(String accountId) {

        Optional<LinkedHashMap<String, String>> row = findRow(accountId);
        if (row.isEmpty()) {
            return false;
        }

        String lockedUntil = row.get().get("LOCKED_UNTIL");
        if (lockedUntil == null || LocalDateTime.parse(lockedUntil, DATE_FORMAT).isBefore(LocalDateTime.now())) {
            if (lockedUntil != null) {
                logger.warn(msg.get("msg.warn.web.twoFactor.accountLockExpired", accountId));
                jdbcTemplate.update(
                        "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?",
                        row.get().get("ACCNT_AUTH_LOCK_ID"));
            }
            return false;
        }
        return true;
    }

    /** 認証失敗を記録します。失敗回数が上限に達した場合はロック解除予定時刻を設定します。 */
    public void recordFailure(String accountId) {

        Optional<LinkedHashMap<String, String>> row = findRow(accountId);
        if (row.isEmpty()) {
            String currentDate = LocalDateTime.now().format(DATE_FORMAT);
            jdbcTemplate.update("""
                    INSERT INTO ACCNT_AUTH_LOCK
                        (ACCNT_ID, FAIL_CNT, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                    VALUES (?, 1, 1, 0, ?, ?, ?, ?)
                    """, accountId, accountId, currentDate, accountId, currentDate);
            return;
        }

        int failCount = Integer.parseInt(row.get().get("FAIL_CNT")) + 1;
        if (failCount >= MAX_FAIL_COUNT) {
            String lockedUntil = LocalDateTime.now().plusMinutes(15).format(DATE_FORMAT);
            jdbcTemplate.update(
                    "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = ?, LOCKED_UNTIL = ? WHERE ACCNT_AUTH_LOCK_ID = ?",
                    failCount, lockedUntil, row.get().get("ACCNT_AUTH_LOCK_ID"));
        } else {
            jdbcTemplate.update("UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = ? WHERE ACCNT_AUTH_LOCK_ID = ?",
                    failCount, row.get().get("ACCNT_AUTH_LOCK_ID"));
        }
    }

    /** 認証成功時に失敗回数・ロック状態をクリアします(行が無ければ何もしません)。 */
    public void resetFailCountOnSuccess(String accountId) {
        findRow(accountId).ifPresent(row -> jdbcTemplate.update(
                "UPDATE ACCNT_AUTH_LOCK SET FAIL_CNT = 0, LOCKED_UNTIL = NULL WHERE ACCNT_AUTH_LOCK_ID = ?",
                row.get("ACCNT_AUTH_LOCK_ID")));
    }

    /** ログイン成功により不要となった行を物理削除します(行が無くても何もしません)。 */
    public void deleteForAccount(String accountId) {
        jdbcTemplate.update("DELETE FROM ACCNT_AUTH_LOCK WHERE ACCNT_ID = ?", accountId);
    }

    private Optional<LinkedHashMap<String, String>> findRow(String accountId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_SQL, List.of(accountId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
