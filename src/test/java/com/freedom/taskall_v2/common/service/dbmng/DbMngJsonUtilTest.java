package com.freedom.taskall_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DbMngJsonUtilTest {

    @Mock
    private ObjectMapper objectMapper;

    private final MsgUtil msg = new MsgUtil();

    @Test
    void コンテキストJSONをObjectNodeとして読み書きしtableNameListを取得できること() {

        ObjectMapper realObjectMapper = JsonMapper.builder().build();
        ObjectNode context = DbMngJsonUtil.readAsObjectNode(realObjectMapper, msg,
                "{\"tableNameList\":[\"ACCNT\",\"APROLE\"],\"requestKind\":\"GET\"}");

        assertThat(DbMngJsonUtil.readTableNameList(context)).containsExactly("ACCNT", "APROLE");
        assertThat(DbMngJsonUtil.writeAsString(realObjectMapper, msg, context))
                .contains("\"tableNameList\":[\"ACCNT\",\"APROLE\"]");
    }

    @Test
    void 不正なJSON文字列を読み込む場合はApplicationInternalExceptionがスローされること() {

        assertThatThrownBy(() -> DbMngJsonUtil.readAsObjectNode(JsonMapper.builder().build(), msg, "{"))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void JSON文字列への書き出しに失敗した場合はApplicationInternalExceptionがスローされること() throws Exception {

        ObjectNode node = JsonMapper.builder().build().createObjectNode();
        when(objectMapper.writeValueAsString(node)).thenThrow(new JacksonException("failed") {
            private static final long serialVersionUID = 1L;
        });

        assertThatThrownBy(() -> DbMngJsonUtil.writeAsString(objectMapper, msg, node))
                .isInstanceOf(ApplicationInternalException.class);
    }
}
