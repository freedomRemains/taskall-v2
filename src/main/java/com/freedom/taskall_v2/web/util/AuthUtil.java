package com.freedom.taskall_v2.web.util;

import java.util.List;
import java.util.Map;

/**
 * 権限判定ユーティリティです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.util.AuthUtil}のうち、DBアクセスを伴わない
 * {@code hasAuth}/{@code hasReadAuth}/{@code hasEditAuth}のみを移植しています。DB取得系の
 * メソッドは{@code GetAccountService}が既にカバーしているため対象外です。Thymeleafテンプレート
 * からはSpringELの静的メソッド構文({@code T(...).hasReadAuth(...)})で呼び出されます。
 * </p>
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    /**
     * アカウントが指定した画面パーツに対する権限(read/editいずれか)を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト({@code HTML_PARTS_ID}/{@code AUTH_KIND}を含む)
     * @return 権限を持っている場合は{@code true}
     */
    public static boolean hasAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        // 権限一覧を走査し、対象パーツIDが1件でも含まれていれば権限ありと判定する。
        for (Map<String, Object> row : authList) {
            if (htmlPartsId.equals(row.get("HTML_PARTS_ID"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * アカウントが指定した画面パーツに対するread権限を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト
     * @return read権限を持っている場合は{@code true}
     */
    public static boolean hasReadAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        return hasAuthKind(htmlPartsId, "read", authList);
    }

    /**
     * アカウントが指定した画面パーツに対するedit権限を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト
     * @return edit権限を持っている場合は{@code true}
     */
    public static boolean hasEditAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        return hasAuthKind(htmlPartsId, "edit", authList);
    }

    private static boolean hasAuthKind(String htmlPartsId, String authKind, List<Map<String, Object>> authList) {
        // パーツIDと権限種別の両方が一致する行を探し、該当があれば権限ありと判定する。
        for (Map<String, Object> row : authList) {
            if (htmlPartsId.equals(row.get("HTML_PARTS_ID")) && authKind.equals(row.get("AUTH_KIND"))) {
                return true;
            }
        }
        return false;
    }
}
