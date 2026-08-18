package com.freedom.taskall_v2.web.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;

/**
 * 通知メッセージの発行・登録を行うサービスです。
 *
 * <p>
 * {@link ErrMsgService}のエラーメッセージ版に相当する仕組みを、正常系の通知向けに提供します。
 * {@code GNR_KEY_VAL}(汎用通知グループ)に定義済みの定型文言を{@code NTC}へ書き込み、発行した
 * キーをリダイレクト先のクエリパラメータ(例: {@code noticeKey})へ付与することで、PRGパターンの
 * 遷移先画面に通知を表示できるようにします。
 * </p>
 */
@Service
public class NoticeService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String GNR_VAL_SQL = "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY_VAL_ID = ?";

    private static final String INSERT_NTC_SQL = """
            INSERT INTO NTC
                (SESSION_ID, ACCNT_ID, NOTICE_MSG, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
            VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public NoticeService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汎用キー値マスタから通知メッセージを取得し、{@code NTC}へ登録した上で
     * 登録したレコードのIDを返却します。
     *
     * @param sessionId   セッションID
     * @param accountId   アカウントID
     * @param gnrKeyValId 汎用キー値マスタID({@code GNR_KEY_VAL_ID})
     * @return 登録した通知のID(文字列)。汎用キー値マスタにメッセージが存在しない場合は{@code "0"}
     */
    public String getNoticeKey(String sessionId, String accountId, String gnrKeyValId) {

        // 汎用キー値マスタから通知本文を取得し、未登録ならダミー値を返却する
        List<LinkedHashMap<String, String>> gnrValRows = recordQueryService.select(GNR_VAL_SQL, List.of(gnrKeyValId));
        if (gnrValRows.isEmpty()) {
            return "0";
        }

        // 取得したメッセージをNTCへ登録し、採番された通知IDを控える
        String noticeMsg = gnrValRows.get(0).get("GNR_VAL");
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_NTC_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, accountId);
            ps.setString(3, noticeMsg);
            ps.setString(4, accountId);
            ps.setString(5, currentDate);
            ps.setString(6, accountId);
            ps.setString(7, currentDate);
            return ps;
        }, keyHolder);

        return String.valueOf(keyHolder.getKey().longValue());
    }
}
