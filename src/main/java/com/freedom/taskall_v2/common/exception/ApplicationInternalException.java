package com.freedom.taskall_v2.common.exception;

/**
 * システム的なエラーを表す例外です。
 *
 * <p>
 * ファイル入出力の失敗など、業務ルールとは関係のない技術的な異常が発生した場合にスローします。
 * throwsを記述せずに済むよう、RuntimeExceptionを継承しています。
 * </p>
 */
public class ApplicationInternalException extends RuntimeException {

    public ApplicationInternalException(String message) {
        super(message);
    }

    public ApplicationInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
