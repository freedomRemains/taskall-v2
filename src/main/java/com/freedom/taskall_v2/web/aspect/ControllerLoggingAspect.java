package com.freedom.taskall_v2.web.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * コントローラの処理時間を計測してログに記録するAspectです。
 *
 * <p>
 * 対象は{@code com.freedom.taskall_v2.web.controller}配下のコントローラが持つ全ての公開メソッド
 * (URIパターンに対応する{@code @GetMapping}/{@code @PostMapping}メソッド)です。開始時のログは
 * {@code TaskallV2Controller#logRequestInfo}で既に出力しているため、本Aspectでは処理終了時に
 * かかった時間(ミリ秒)のみをINFOレベルで記録します。
 * </p>
 */
@Aspect
@Component
public class ControllerLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerLoggingAspect.class);

    @Around("execution(public * com.freedom.taskall_v2.web.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {

        // コントローラメソッドの実行前後の時刻から所要時間を計測し、例外発生時も計測結果を記録する
        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            logger.info("[ControllerExecutionTime] method={}, elapsedMillis={}",
                    joinPoint.getSignature().toShortString(), elapsedMillis);
        }
    }
}
