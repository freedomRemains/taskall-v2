package com.freedom.taskall_v2.common.db.aspect;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link org.springframework.jdbc.core.JdbcTemplate}によるSQL実行の前後に介入し、SQL文と
 * バインドパラメータ、および実行にかかった時間(ミリ秒)をログに記録するAspectです。
 *
 * <p>
 * {@code RecordQueryService}(SELECT専用)、及び各Write系サービスはいずれも
 * {@link org.springframework.jdbc.core.JdbcTemplate}を直接注入して利用しているため、
 * {@code RecordQueryService}単体ではなく{@code JdbcTemplate}側にAspectを適用することで、
 * 呼び出し元を問わず一箇所でSQLログを集約できます。対象は本プロジェクトの各サービスが実際に
 * 使用しているメソッドシグネチャ({@code queryForList}/{@code update}/{@code execute}/
 * {@code batchUpdate})に限定し、これらのメソッドが内部的に委譲する他のオーバーロードは
 * Spring AOPのプロキシ経由にならない自己呼び出しのため、二重ログの心配はありません。
 * </p>
 */
@Aspect
@Component
public class SqlLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(SqlLoggingAspect.class);

    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.queryForList(java.lang.String, java.lang.Object...))"
            + " || execution(* org.springframework.jdbc.core.JdbcTemplate.update(java.lang.String, java.lang.Object...))"
            + " || execution(* org.springframework.jdbc.core.JdbcTemplate.update("
            + "org.springframework.jdbc.core.PreparedStatementCreator, org.springframework.jdbc.support.KeyHolder))"
            + " || execution(* org.springframework.jdbc.core.JdbcTemplate.execute(java.lang.String))"
            + " || execution(* org.springframework.jdbc.core.JdbcTemplate.batchUpdate(java.lang.String...))")
    public Object logSqlExecution(ProceedingJoinPoint joinPoint) throws Throwable {

        // SQL実行前に、SQL文とバインドパラメータをログ出力する
        Object[] args = joinPoint.getArgs();
        logger.info("[SqlExecution] sql={}, params={}", extractSql(args), extractParams(args));

        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            logger.info("[SqlExecutionTime] elapsedMillis={}", elapsedMillis);
        }
    }

    private String extractSql(Object[] args) {

        // 先頭引数がSQL文字列の場合はそのまま、String配列(batchUpdate)の場合は連結して返却する。
        // PreparedStatementCreatorのようにSQLを直接引数に持たない呼び出しは、その旨を返却する
        if (args.length == 0) {
            return "";
        }
        if (args[0] instanceof String sql) {
            return sql;
        }
        if (args[0] instanceof String[] sqlArray) {
            return String.join(" ; ", sqlArray);
        }
        return "(SQL文を直接取得できない呼び出しです。type=" + args[0].getClass().getName() + ")";
    }

    private String extractParams(Object[] args) {

        // 第2引数がバインドパラメータ(可変長引数)の場合のみ、その内容を文字列化して返却する
        if (args.length < 2) {
            return "";
        }
        if (args[1] instanceof Object[] params) {
            return Arrays.toString(params);
        }
        return "";
    }
}
