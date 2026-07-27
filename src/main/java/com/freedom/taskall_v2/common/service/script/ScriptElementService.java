package com.freedom.taskall_v2.common.service.script;

/**
 * スクリプト(SCR/SCR_ELM)から実行される、個々の業務ロジックを表すインターフェースです。
 *
 * <p>
 * 移植元「remainz」の{@code ServiceInterface}に相当します。移植元では入出力に
 * {@code GenericParam}を使用していましたが、本プロジェクトではString型のJSON文字列に
 * 置き換えています。ある業務ロジックの出力JSONは、次の業務ロジックの入力JSONにマージされます。
 * </p>
 */
public interface ScriptElementService {

    /**
     * 業務ロジックを実行します。
     *
     * @param contextJson それまでのスクリプト実行結果を含む入力JSON文字列
     * @return 出力(コンテキストにマージする差分/更新後の値)を含むJSON文字列
     */
    String execute(String contextJson);
}
