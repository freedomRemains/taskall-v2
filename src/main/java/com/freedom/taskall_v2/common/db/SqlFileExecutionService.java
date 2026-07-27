package com.freedom.taskall_v2.common.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * SQLファイルの内容を、一定件数(バッチ)ごとに区切ってDBへ実行するクラスです。
 *
 * <p>
 * {@code DbInitializationService}はクラスパス上の少量の初期シードSQLを一括実行しますが、
 * 本クラスはDBメンテナンス機能(DB構成更新)で実行時に生成される、件数の多いSQLファイル
 * (INSERT文が大量に並ぶ等)を対象とするため、1回のバッチ実行あたりの文数を制限し、
 * メモリ・DB双方への負荷を抑えます。
 * </p>
 */
@Service
public class SqlFileExecutionService {

    /** 1回のバッチ実行あたりの最大SQL文数 */
    public static final int BATCH_SIZE = 5000;

    private final JdbcTemplate jdbcTemplate;
    private final MsgUtil msg;

    public SqlFileExecutionService(JdbcTemplate jdbcTemplate, MsgUtil msg) {
        this.jdbcTemplate = jdbcTemplate;
        this.msg = msg;
    }

    /**
     * SQLファイルの内容をセミコロン区切りで分割し、{@link #BATCH_SIZE}件ずつバッチ実行します。
     *
     * @param sqlFilePath 実行対象のSQLファイルパス
     */
    public void execute(Path sqlFilePath) {

        // ファイルが存在しない場合(対象テーブルにデータが無くINSERTファイルが生成されなかった等)は
        // 何もせずスキップする
        if (!Files.exists(sqlFilePath)) {
            return;
        }

        List<String> statements = readStatements(sqlFilePath);
        executeBatched(statements);
    }

    private List<String> readStatements(Path sqlFilePath) {
        try {
            String content = Files.readString(sqlFilePath, StandardCharsets.UTF_8);
            List<String> statements = new ArrayList<>();
            for (String statement : content.split(";")) {
                String trimmed = statement.strip();
                if (!trimmed.isEmpty()) {
                    statements.add(trimmed + ";");
                }
            }
            return statements;
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.sqlFileReadFailed", sqlFilePath), e);
        }
    }

    private void executeBatched(List<String> statements) {
        // 1回あたりのバッチ実行件数をBATCH_SIZEに制限し、大量のSQL文でもDBへの負荷を抑えて実行する
        for (int fromIndex = 0; fromIndex < statements.size(); fromIndex += BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_SIZE, statements.size());
            List<String> batch = statements.subList(fromIndex, toIndex);
            jdbcTemplate.batchUpdate(batch.toArray(new String[0]));
        }
    }
}
