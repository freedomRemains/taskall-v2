package com.freedom.taskall_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link AuthUtil}のテストです。
 */
class AuthUtilTest {

    private Map<String, Object> authRow(String htmlPartsId, String authKind) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("HTML_PARTS_ID", htmlPartsId);
        row.put("AUTH_KIND", authKind);
        return row;
    }

    @Test
    void hasAuthは対象のHTML_PARTS_IDが権限一覧に存在すればtrueを返すこと() {

        List<Map<String, Object>> authList = List.of(authRow("1000001", "read"));

        assertThat(AuthUtil.hasAuth("1000001", authList)).isTrue();
        assertThat(AuthUtil.hasAuth("9999999", authList)).isFalse();
    }

    @Test
    void hasReadAuthはAUTH_KINDがreadの場合のみtrueを返すこと() {

        List<Map<String, Object>> authList = List.of(authRow("1000201", "edit"));

        assertThat(AuthUtil.hasReadAuth("1000201", authList)).isFalse();
        assertThat(AuthUtil.hasEditAuth("1000201", authList)).isTrue();
    }

    @Test
    void 権限一覧が空の場合はいずれもfalseを返すこと() {

        List<Map<String, Object>> authList = List.of();

        assertThat(AuthUtil.hasAuth("1000001", authList)).isFalse();
        assertThat(AuthUtil.hasReadAuth("1000001", authList)).isFalse();
        assertThat(AuthUtil.hasEditAuth("1000001", authList)).isFalse();
    }
}
