package com.freedom.taskall_v2.common.exception;

/**
 * 業務的なエラーを表す例外です。
 *
 * <p>
 * 入力JSONに必須パラメータが存在しない、といった業務ルール違反を表す場合にスローします。
 * throwsを記述せずに済むよう、RuntimeExceptionを継承しています。
 * </p>
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
