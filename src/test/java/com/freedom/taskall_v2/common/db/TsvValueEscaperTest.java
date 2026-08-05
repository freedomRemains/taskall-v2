package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;

class TsvValueEscaperTest {

    private final TsvValueEscaper tsvValueEscaper = new TsvValueEscaper();

    @Test
    void encodeはCRとLFとタブをそれぞれ所定の文字列に変換すること() {

        String encoded = tsvValueEscaper.encode("a\rb\nc\td");

        assertThat(encoded).isEqualTo("a#Yr#b#Yn#c#Yt#d");
    }

    @Test
    void encodeは変換対象文字が含まれない値をそのまま返すこと() {

        assertThat(tsvValueEscaper.encode("そのまま")).isEqualTo("そのまま");
    }

    @Test
    void encodeは値に変換後文字列と同じ文字列が含まれる場合は業務エラーとすること() {

        assertThatThrownBy(() -> tsvValueEscaper.encode("foo#Yr#bar"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> tsvValueEscaper.encode("foo#Yn#bar"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> tsvValueEscaper.encode("foo#Yt#bar"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void decodeは所定の文字列をCRとLFとタブへそれぞれ変換すること() {

        String decoded = tsvValueEscaper.decode("a#Yr#b#Yn#c#Yt#d");

        assertThat(decoded).isEqualTo("a\rb\nc\td");
    }

    @Test
    void decodeは変換対象文字列が含まれない値をそのまま返すこと() {

        assertThat(tsvValueEscaper.decode("そのまま")).isEqualTo("そのまま");
    }
}
