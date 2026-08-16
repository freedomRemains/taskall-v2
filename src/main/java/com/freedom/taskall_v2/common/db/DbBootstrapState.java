package com.freedom.taskall_v2.common.db;

import org.springframework.stereotype.Component;

/**
 * このアプリ起動処理内で、{@link DbInitializer}が「TBL_DEF」テーブルを新規作成したか
 * (＝真の初回起動だったか)を、後続の処理(issue #72の{@code FlywayMigrationRunner})へ
 * 伝えるための状態保持クラスです。
 *
 * <p>
 * 新規作成直後のDBは、常に「db/data」配下の最新資材を反映した状態(最新スキーマ)になるため、
 * Flyway側はこれを「既存の最新バージョンまでのマイグレーションが適用済みの状態」として
 * ベースライン化する必要があります。一方、既に「TBL_DEF」が存在していた場合(既存の本番DB等)は、
 * 「V1(Flyway導入以前の状態)」としてベースライン化したうえで、未適用のマイグレーションのみを
 * 適用する必要があります。この判定の橋渡し役として、Spring管理の単一インスタンスに状態を保持します。
 * </p>
 */
@Component
public class DbBootstrapState {

    /** このアプリ起動処理内で、TBL_DEFテーブルを新規作成した(真の初回起動だった)場合はtrue */
    private boolean freshlyBootstrapped;

    public boolean isFreshlyBootstrapped() {
        return freshlyBootstrapped;
    }

    public void setFreshlyBootstrapped(boolean freshlyBootstrapped) {
        this.freshlyBootstrapped = freshlyBootstrapped;
    }
}
