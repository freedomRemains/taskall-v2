package com.freedom.taskall_v2.web.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * コントローラで発生した例外を一括して処理するグローバル例外ハンドラです。
 *
 * <p>
 * 移植元「remainz」の{@code ServiceControlServlet#analyzeUri}が行っていた、例外発生時の
 * ログ記録・エラーページ遷移をSpringBootのグローバル例外ハンドラとして集約しています。移植元は
 * デバッグの都合を優先しエラー画面にスタックトレースを表示していましたが、本クラスでは例外の詳細は
 * ログにのみ記録し、画面には固定文言のみを表示します。
 * </p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** エラー画面のビュー名(常に固定文言のみを表示する単純な画面) */
    private static final String ERROR_VIEW = "error";

    private final MsgUtil msg;

    public GlobalExceptionHandler(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * 業務ルール違反(必須パラメータ欠如など)を処理します。
     *
     * @param e 発生した業務例外
     * @return エラー画面のビュー名
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleBusinessRuleViolation(BusinessRuleViolationException e) {
        // 業務ルール違反はシステム自体の運用に支障はないため、警告レベルで記録する
        logger.warn(msg.get("msg.warn.web.businessRuleViolation", e.getMessage()), e);
        return ERROR_VIEW;
    }

    /**
     * システム的なエラー(IOExceptionなど技術的な失敗)を処理します。
     *
     * @param e 発生したシステム例外
     * @return エラー画面のビュー名
     */
    @ExceptionHandler(ApplicationInternalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleApplicationInternalError(ApplicationInternalException e) {
        logger.error(msg.get("msg.err.web.applicationInternalError", e.getMessage()), e);
        return ERROR_VIEW;
    }

    /**
     * 上記2種以外の、予期せぬ例外を全て処理します。
     *
     * @param e 発生した予期せぬ例外
     * @return エラー画面のビュー名
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpectedException(Exception e) {
        logger.error(msg.get("msg.err.web.unexpectedException", e.getMessage()), e);
        return ERROR_VIEW;
    }
}
