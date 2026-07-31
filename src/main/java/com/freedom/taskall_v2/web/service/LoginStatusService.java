package com.freedom.taskall_v2.web.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;

/**
 * 「ログイン試行(LOGIN_STATUS)」テーブルの読み書きを行うサービスです。
 *
 * <p>
 * 1回のログイン試行(=1ブラウザセッション)の状態を{@code (ACCNT_ID, SESSION_ID)}単位で
 * 管理します。アカウント単位の失敗回数・ロック状態は{@link AccntAuthLockService}が別途
 * 管理するため、本クラスでは扱いません。
 * </p>
 */
@Service
public class LoginStatusService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIND_SQL =
            "SELECT LOGIN_STATUS_ID, CURRENT_STATUS, PASSCODE_HASH, EXPIRES_AT "
                    + "FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?";

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public LoginStatusService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * このセッションでのログイン試行を開始します。有効な行が無ければ新規作成し、
     * 有効期限内の行が既に存在すればそのまま返却します。
     */
    public LinkedHashMap<String, String> beginAttempt(String accountId, String sessionId) {

        // 既存の行を検索する
        Optional<LinkedHashMap<String, String>> existing = findRow(accountId, sessionId);
        if (existing.isPresent() && !isExpired(existing.get())) {
            return existing.get();
        }

        // 期限切れの行が存在すれば削除する
        existing.ifPresent(row -> jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?",
                row.get("LOGIN_STATUS_ID")));

        // not_auth状態で新規作成する
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        String expiresAt = LocalDateTime.now().plusMinutes(30).format(DATE_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO LOGIN_STATUS
                    (ACCNT_ID, SESSION_ID, CURRENT_STATUS, EXPIRES_AT, VERSION, IS_DELETED,
                     CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
                VALUES (?, ?, 'not_auth', ?, 1, 0, ?, ?, ?, ?)
                """, accountId, sessionId, expiresAt, accountId, currentDate, accountId, currentDate);

        return findRow(accountId, sessionId)
                .orElseThrow(() -> new IllegalStateException("LOGIN_STATUS行の作成直後の再取得に失敗しました。"));
    }

    /** 一次認証通過時の状態へ更新します(有効期限を5分後へ短縮し、パスコードハッシュを保存する)。 */
    public void markFirstAuthPass(String loginStatusId, String passcodeHash) {
        String expiresAt = LocalDateTime.now().plusMinutes(5).format(DATE_FORMAT);
        updateStatus(loginStatusId, "first_auth_pass", passcodeHash, expiresAt);
    }

    /** 一次認証失敗時の状態へ更新します(有効期限は変更しません)。 */
    public void markFirstAuthFail(String loginStatusId) {
        jdbcTemplate.update("UPDATE LOGIN_STATUS SET CURRENT_STATUS = 'first_auth_fail' WHERE LOGIN_STATUS_ID = ?",
                loginStatusId);
    }

    /**
     * 二次認証の照合対象として、有効な行を検索します。行が無い・期限切れ・不正な遷移
     * (現在ステータスがfirst_auth_pass/second_auth_fail以外)のいずれかの場合は空を返します。
     * 期限切れの場合は行を物理削除した上で空を返します。
     */
    public Optional<LinkedHashMap<String, String>> findForVerification(String accountId, String sessionId) {

        // (ACCNT_ID, SESSION_ID)で行を検索する
        Optional<LinkedHashMap<String, String>> row = findRow(accountId, sessionId);
        if (row.isEmpty()) {
            return Optional.empty();
        }

        // 期限切れの場合は物理削除して空を返す
        if (isExpired(row.get())) {
            jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE LOGIN_STATUS_ID = ?", row.get().get("LOGIN_STATUS_ID"));
            return Optional.empty();
        }

        // 二次認証の再入力待機中(second_auth_fail)も、一次認証通過直後(first_auth_pass)と
        // 同様に有効な検証対象として扱う(一度誤入力しただけで再入力できなくなる不具合を防ぐ)
        String currentStatus = row.get().get("CURRENT_STATUS");
        if (!"first_auth_pass".equals(currentStatus) && !"second_auth_fail".equals(currentStatus)) {
            return Optional.empty();
        }

        return row;
    }

    /** 二次認証失敗時の状態へ更新します(有効期限は変更しません)。 */
    public void markSecondAuthFail(String loginStatusId) {
        jdbcTemplate.update("UPDATE LOGIN_STATUS SET CURRENT_STATUS = 'second_auth_fail' WHERE LOGIN_STATUS_ID = ?",
                loginStatusId);
    }

    /** 役目を終えた行(二次認証成功時)を物理削除します。 */
    public void deleteFor(String accountId, String sessionId) {
        jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE ACCNT_ID = ? AND SESSION_ID = ?", accountId, sessionId);
    }

    /** 有効期限切れの行を一括で物理削除し、削除件数を返します(定期クリーンアップ処理から呼び出す)。 */
    public int deleteExpired() {
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        return jdbcTemplate.update("DELETE FROM LOGIN_STATUS WHERE EXPIRES_AT < ?", currentDate);
    }

    /** (ACCNT_ID, SESSION_ID)で行を検索する */
    private Optional<LinkedHashMap<String, String>> findRow(String accountId, String sessionId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_SQL, List.of(accountId, sessionId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 行の有効期限が切れているかどうかを判定する */
    private boolean isExpired(LinkedHashMap<String, String> row) {
        LocalDateTime expiresAt = java.time.LocalDateTime.parse(row.get("EXPIRES_AT"), DATE_FORMAT);
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /** ステータス・パスコードハッシュ・有効期限を更新する */
    private void updateStatus(String loginStatusId, String status, String passcodeHash, String expiresAt) {
        jdbcTemplate.update(
                "UPDATE LOGIN_STATUS SET CURRENT_STATUS = ?, PASSCODE_HASH = ?, EXPIRES_AT = ? WHERE LOGIN_STATUS_ID = ?",
                status, passcodeHash, expiresAt, loginStatusId);
    }
}
