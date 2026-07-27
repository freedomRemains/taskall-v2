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
 * エラーメッセージの発行・登録を行うサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.util.ErrMsgUtil}に相当します。{@code GNR_KEY_VAL}の
 * 参照と{@code ERR_MSG}への書込みの両方を行うため、静的utilではなく{@code RecordQueryService}
 * (SELECT専用)と{@link JdbcTemplate}(INSERT用)を注入した{@code @Service}として実装します。
 * </p>
 */
@Service
public class ErrMsgService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String GNR_VAL_SQL = "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY_VAL_ID = ?";

    private static final String INSERT_ERR_MSG_SQL = """
            INSERT INTO ERR_MSG
                (SESSION_ID, ACCNT_ID, ERR_MSG, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
            VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public ErrMsgService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汎用キー値マスタからエラーメッセージを取得し、{@code ERR_MSG}へ登録した上で
     * 登録したレコードのIDを返却します。
     *
     * @param sessionId   セッションID
     * @param accountId   アカウントID
     * @param gnrKeyValId 汎用キー値マスタID({@code GNR_KEY_VAL_ID})
     * @return 登録したエラーメッセージのID(文字列)。汎用キー値マスタにメッセージが存在しない場合は{@code "0"}
     */
    public String getErrMsgKey(String sessionId, String accountId, String gnrKeyValId) {

        // 汎用キー値マスタからエラーメッセージ本文を取得し、未登録ならダミー値を返却する
        List<LinkedHashMap<String, String>> gnrValRows = recordQueryService.select(GNR_VAL_SQL, List.of(gnrKeyValId));
        if (gnrValRows.isEmpty()) {
            return "0";
        }

        // 取得したメッセージをERR_MSGへ登録し、採番されたエラーメッセージIDを控える
        String errMsg = gnrValRows.get(0).get("GNR_VAL");
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_ERR_MSG_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, accountId);
            ps.setString(3, errMsg);
            ps.setString(4, accountId);
            ps.setString(5, currentDate);
            ps.setString(6, accountId);
            ps.setString(7, currentDate);
            return ps;
        }, keyHolder);

        return String.valueOf(keyHolder.getKey().longValue());
    }
}
