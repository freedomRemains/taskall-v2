package com.freedom.taskall_v2.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link VariablePlaceholderResolver}のテストです。
 */
class VariablePlaceholderResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MsgUtil msg = new MsgUtil();

    @Test
    void プレースホルダーがコンテキストの値で置換されること() {

        ObjectNode context = objectMapper.createObjectNode();
        context.put("tableName", "ACCNT");

        String result = VariablePlaceholderResolver.resolve("SELECT * FROM #{tableName}", context, msg);

        assertThat(result).isEqualTo("SELECT * FROM ACCNT");
    }

    @Test
    void 複数のプレースホルダーが全て置換されること() {

        ObjectNode context = objectMapper.createObjectNode();
        context.put("tableName", "ACCNT");
        context.put("recordId", "1000001");

        String result = VariablePlaceholderResolver.resolve(
                "SELECT * FROM #{tableName} WHERE #{tableName}_ID = #{recordId}", context, msg);

        assertThat(result).isEqualTo("SELECT * FROM ACCNT WHERE ACCNT_ID = 1000001");
    }

    @Test
    void アンダーバーを含む物理カラム名のキーでもプレースホルダーが置換されること() {

        ObjectNode context = objectMapper.createObjectNode();
        context.put("TABLE_NAME", "ACCNT");

        String result = VariablePlaceholderResolver.resolve(
                "/taskall-v2/service/tableDefRef.html?tableName=#{TABLE_NAME}", context, msg);

        assertThat(result).isEqualTo("/taskall-v2/service/tableDefRef.html?tableName=ACCNT");
    }

    @Test
    void 対応する値が見つからない場合はプレースホルダーがそのまま残ること() {

        ObjectNode context = objectMapper.createObjectNode();

        String result = VariablePlaceholderResolver.resolve("SELECT * FROM #{tableName}", context, msg);

        assertThat(result).isEqualTo("SELECT * FROM #{tableName}");
    }

    @Test
    void プレースホルダーが含まれない文字列はそのまま返却されること() {

        String result = VariablePlaceholderResolver.resolve("SELECT * FROM ACCNT", objectMapper.createObjectNode(), msg);

        assertThat(result).isEqualTo("SELECT * FROM ACCNT");
    }

    @Test
    void nullを渡した場合はnullが返却されること() {

        String result = VariablePlaceholderResolver.resolve(null, objectMapper.createObjectNode(), msg);

        assertThat(result).isNull();
    }
}
