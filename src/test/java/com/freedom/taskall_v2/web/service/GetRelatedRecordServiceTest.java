package com.freedom.taskall_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.taskall_v2.common.db.RecordQueryService;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link GetRelatedRecordService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class GetRelatedRecordServiceTest {

    private static final String FOREIGN_TABLE_DEF_SQL = "SELECT * FROM TBL_DEF WHERE FOREIGN_TABLE = ?";
    private static final String TABLE_DEF_SQL = "SELECT * FROM TBL_DEF WHERE TABLE_NAME = ?";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private ErrMsgService errMsgService;

    @Mock
    private TableNameValidator tableNameValidator;

    private GetRelatedRecordService getRelatedRecordService;

    @BeforeEach
    void setUp() {
        getRelatedRecordService = new GetRelatedRecordService(recordQueryService, errMsgService, tableNameValidator,
                JsonMapper.builder().build(), new MsgUtil());
    }

    private LinkedHashMap<String, String> tblDefRow(String tableName, String tableLogicalName, String fieldName,
            String fieldLogicalName, String foreignTable, String descField) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("TABLE_NAME", tableName);
        row.put("TABLE_LOGICAL_NAME", tableLogicalName);
        row.put("FIELD_NAME", fieldName);
        row.put("FIELD_LOGICAL_NAME", fieldLogicalName);
        row.put("FOREIGN_TABLE", foreignTable);
        row.put("DESC_FIELD", descField);
        return row;
    }

    @Test
    void 対象レコードが存在しない場合は一覧画面へPRGパターンでリダイレクトされること() {

        when(recordQueryService.select(eq("SELECT * FROM ACCNT WHERE ACCNT_ID = ?"), eq(List.of("9999999"))))
                .thenReturn(new ArrayList<>());
        when(errMsgService.getErrMsgKey(eq("session-1"), eq("1000001"), eq("1000301"))).thenReturn("123");

        String contextJson = """
                {"tableName":"ACCNT","recordId":"9999999","sessionId":"session-1","accountId":"1000001"}
                """;

        String result = getRelatedRecordService.execute(contextJson);

        assertThat(result).contains("\"respKind\":\"redirect\"");
        assertThat(result)
                .contains("\"destination\":\"/taskall-v2/service/tableDataMainte.html?tableName=ACCNT&errMsgKey=123\"");
    }

    @Test
    void DESC_FIELDを持つ参照元テーブルはそのままレコード一覧として表示されること() {

        when(recordQueryService.select(eq("SELECT * FROM ACCNT WHERE ACCNT_ID = ?"), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(tblDefRow("ACCNT", "アカウント", "ACCNT_ID", "アカウントID", null, null))));

        when(recordQueryService.select(eq(FOREIGN_TABLE_DEF_SQL), eq(List.of("ACCNT"))))
                .thenReturn(new ArrayList<>(
                        List.of(tblDefRow("NOTICE", "お知らせ", "ACCNT_ID", "アカウントID", "ACCNT", "NOTICE_TITLE"))));

                ArrayList<LinkedHashMap<String, String>> noticeDefList = new ArrayList<>(List.of(
                tblDefRow("NOTICE", "お知らせ", "NOTICE_ID", "お知らせID", null, "NOTICE_TITLE"),
                tblDefRow("NOTICE", "お知らせ", "ACCNT_ID", "アカウントID", "ACCNT", "NOTICE_TITLE"),
                tblDefRow("NOTICE", "お知らせ", "NOTICE_TITLE", "お知らせタイトル", null, "NOTICE_TITLE")));
        when(recordQueryService.select(eq(TABLE_DEF_SQL), eq(List.of("NOTICE")))).thenReturn(noticeDefList);

        LinkedHashMap<String, String> noticeRow = new LinkedHashMap<>();
        noticeRow.put("NOTICE_ID", "2000001");
        noticeRow.put("NOTICE_TITLE", "メンテナンスのお知らせ");
        noticeRow.put("ACCNT_ID", "1000001");
        when(recordQueryService.select(eq("SELECT * FROM NOTICE WHERE ACCNT_ID = ?"), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(noticeRow)));

        String contextJson = """
                {"tableName":"ACCNT","recordId":"1000001","sessionId":"session-1","accountId":"1000001"}
                """;

        String result = getRelatedRecordService.execute(contextJson);

        assertThat(result).contains("\"tableName\":\"NOTICE\"");
        assertThat(result).contains("\"tableLogicalName\":\"お知らせ\"");
        assertThat(result).contains("\"primaryKeyField\":\"NOTICE_ID\"");
        assertThat(result).contains("\"descField\":\"NOTICE_TITLE\"");
        assertThat(result).contains("\"NOTICE_TITLE\":\"メンテナンスのお知らせ\"");
    }

    @Test
    void DESC_FIELDを持たない組み合わせテーブルは1段先の外部テーブルまで追跡されること() {

        when(recordQueryService.select(eq("SELECT * FROM ACCNT WHERE ACCNT_ID = ?"), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(tblDefRow("ACCNT", "アカウント", "ACCNT_ID", "アカウントID", null, null))));

        when(recordQueryService.select(eq(FOREIGN_TABLE_DEF_SQL), eq(List.of("ACCNT")))).thenReturn(new ArrayList<>(
                List.of(tblDefRow("APROLE_IN_ACCNT", "ロール紐付け", "ACCNT_ID", "アカウントID", "ACCNT", ""))));

        ArrayList<LinkedHashMap<String, String>> junctionDefList = new ArrayList<>(List.of(
                tblDefRow("APROLE_IN_ACCNT", "ロール紐付け", "APROLE_IN_ACCNT_ID", "ID", null, ""),
                tblDefRow("APROLE_IN_ACCNT", "ロール紐付け", "ACCNT_ID", "アカウントID", "ACCNT", ""),
                tblDefRow("APROLE_IN_ACCNT", "ロール紐付け", "APROLE_ID", "ロールID", "APROLE", "")));
        when(recordQueryService.select(eq(TABLE_DEF_SQL), eq(List.of("APROLE_IN_ACCNT")))).thenReturn(junctionDefList);

        LinkedHashMap<String, String> junctionRow = new LinkedHashMap<>();
        junctionRow.put("APROLE_IN_ACCNT_ID", "3000001");
        junctionRow.put("ACCNT_ID", "1000001");
        junctionRow.put("APROLE_ID", "1000301");
        when(recordQueryService.select(eq("SELECT * FROM APROLE_IN_ACCNT WHERE ACCNT_ID = ?"), eq(List.of("1000001"))))
                .thenReturn(new ArrayList<>(List.of(junctionRow)));

        ArrayList<LinkedHashMap<String, String>> aproleDefList = new ArrayList<>(List.of(
                tblDefRow("APROLE", "ロール", "APROLE_ID", "ロールID", null, "ROLE_NAME"),
                tblDefRow("APROLE", "ロール", "ROLE_NAME", "ロール名", null, "ROLE_NAME")));
        when(recordQueryService.select(eq(TABLE_DEF_SQL), eq(List.of("APROLE")))).thenReturn(aproleDefList);

        LinkedHashMap<String, String> aproleRow = new LinkedHashMap<>();
        aproleRow.put("APROLE_ID", "1000301");
        aproleRow.put("ROLE_NAME", "管理者");
        when(recordQueryService.select(eq("SELECT APROLE_ID, ROLE_NAME FROM APROLE WHERE APROLE_ID IN (1000301)")))
                .thenReturn(new ArrayList<>(List.of(aproleRow)));

        String contextJson = """
                {"tableName":"ACCNT","recordId":"1000001","sessionId":"session-1","accountId":"1000001"}
                """;

        String result = getRelatedRecordService.execute(contextJson);

        assertThat(result).contains("\"tableName\":\"APROLE_IN_ACCNT\"");
        assertThat(result).contains("\"foreignTableName\":\"APROLE\"");
        assertThat(result).contains("\"primaryKeyField\":\"APROLE_ID\"");
        assertThat(result).contains("\"descField\":\"ROLE_NAME\"");
        assertThat(result).contains("\"ROLE_NAME\":\"管理者\"");
    }

    @Test
    void tableNameが未入力の場合は業務エラーとなること() {

        assertThatThrownBy(() -> getRelatedRecordService.execute("{\"recordId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void recordIdが未入力の場合は業務エラーとなること() {

        assertThatThrownBy(() -> getRelatedRecordService.execute("{\"tableName\":\"ACCNT\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void tableNameが不正な場合は業務エラーとなること() {

        doThrow(new BusinessRuleViolationException("不正なテーブル名です")).when(tableNameValidator).validate("INVALID");

        assertThatThrownBy(
                () -> getRelatedRecordService.execute("{\"tableName\":\"INVALID\",\"recordId\":\"1000001\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(recordQueryService);
    }
}
