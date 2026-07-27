package com.freedom.taskall_v2.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;

class MsgUtilTest {

    private final MsgUtil msgUtil = new MsgUtil();

    @Test
    void 可変引数を織り込んだメッセージが取得できること() {

        String message = msgUtil.get("msg.err.common.db.columnDefNotFound", "SAMPLE");

        assertThat(message).isEqualTo("カラム定義が存在しません。tableName=SAMPLE");
    }

    @Test
    void 可変引数が無いメッセージが取得できること() {

        String message = msgUtil.get("msg.err.common.db.headerRowNotFound");

        assertThat(message).isEqualTo("ヘッダ行が存在しません。");
    }

    @Test
    void 存在しないキーを指定した場合は例外がスローされること() {

        assertThatThrownBy(() -> msgUtil.get("msg.err.common.db.notExistKey"))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
