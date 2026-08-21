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
 * 「アカウント内メールアドレス(MAIL_ADDR_IN_ACCNT)」テーブルを扱うサービスです(issue #96)。
 *
 * <p>
 * 監視対象メールアドレス・暗号化済みパスワードは、アカウント1件につき1行のみ保持する
 * (テーブル定義上{@code ACCNT_ID}に一意制約あり)。登録画面からの入力は常に「新規登録」
 * 「再登録(上書き)」いずれもUPDATE/INSERTの1メソッド({@link #upsert})で吸収する。
 * </p>
 */
@Service
public class MailAddrInAccntService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIND_BY_ACCOUNT_ID_SQL = """
            SELECT MAIL_ADDR_IN_ACCNT_ID, ACCNT_ID, MAIL_ADDR, PASSWORD_ENC, VERSION
            FROM MAIL_ADDR_IN_ACCNT
            WHERE ACCNT_ID = ?
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public MailAddrInAccntService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * アカウントIDに紐づく監視対象メールアドレス行を取得します。
     */
    public Optional<LinkedHashMap<String, String>> findByAccountId(String accountId) {
        List<LinkedHashMap<String, String>> rows = recordQueryService.select(FIND_BY_ACCOUNT_ID_SQL, List.of(accountId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 監視対象メールアドレス・暗号化済みパスワードを登録します。
     *
     * <p>
     * 既存行があればUPDATE(VERSIONを1加算)、無ければINSERTします。呼び出し側で
     * メールボックスへの接続確認が成功した場合のみ呼び出すこと(接続確認に失敗した場合、
     * 既存行は変更せずそのままとする、というissue #96の方針は本メソッドを呼ばないことで実現する)。
     * </p>
     */
    public void upsert(String accountId, String mailAddress, String encryptedPassword) {

        String currentDate = LocalDateTime.now().format(DATE_FORMAT);
        Optional<LinkedHashMap<String, String>> existing = findByAccountId(accountId);

        if (existing.isPresent()) {
            String currentVersion = existing.get().get("VERSION");
            int nextVersion = Integer.parseInt(currentVersion) + 1;
            jdbcTemplate.update("""
                    UPDATE MAIL_ADDR_IN_ACCNT
                    SET MAIL_ADDR = ?, PASSWORD_ENC = ?, VERSION = ?, UPDATED_BY = ?, UPDATED_AT = ?
                    WHERE ACCNT_ID = ?
                    """, mailAddress, encryptedPassword, nextVersion, accountId, currentDate, accountId);
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO MAIL_ADDR_IN_ACCNT
                    (ACCNT_ID, MAIL_ADDR, PASSWORD_ENC, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY,
                     UPDATED_AT)
                VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)
                """, accountId, mailAddress, encryptedPassword, accountId, currentDate, accountId, currentDate);
    }
}
