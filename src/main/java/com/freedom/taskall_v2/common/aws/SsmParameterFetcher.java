package com.freedom.taskall_v2.common.aws;

import java.util.Optional;

/**
 * AWS SSM Parameter Store上のSecureStringパラメータを取得するためのインタフェースです。
 *
 * <p>
 * 移植元「remainz」には存在しない、taskall-v2独自の資材です(issue #41)。
 * 呼び出し側(共通処理)がAWS SDKの{@code SsmClient}に直接依存しないよう、本インタフェースで
 * 抽象化することで、Mockitoベースの単体テストを容易にします({@link AwsSsmParameterFetcher}が
 * AWS SDKを使った実装を提供します)。
 * </p>
 */
public interface SsmParameterFetcher {

    /**
     * 指定したパラメータ名のSecureString値を、復号した状態で取得します。
     *
     * @param parameterName SSMパラメータ名(フルパス)
     * @return パラメータの値。パラメータが存在しない場合は空
     */
    Optional<String> fetchSecureString(String parameterName);
}
