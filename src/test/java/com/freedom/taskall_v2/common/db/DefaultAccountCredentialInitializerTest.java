package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freedom.taskall_v2.common.aws.SsmParameterFetcher;
import com.freedom.taskall_v2.common.config.CredentialInitProperties;
import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.util.MsgUtil;

@ExtendWith(MockitoExtension.class)
class DefaultAccountCredentialInitializerTest {

    @Mock
    private SsmParameterFetcher ssmParameterFetcher;

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CredentialInitProperties properties;

    @Mock
    private ApplicationArguments applicationArguments;

    private MsgUtil msg;

    private DefaultAccountCredentialInitializer initializer;

    /** ACCNT_IDごとの、シードデータ由来の既知のデフォルトメールアドレス(本クラスの定数と同じ内容) */
    private static final Map<String, String> DEFAULT_MAIL_ADDRESS_BY_ID = Map.of(
            "1000001", "guest@account.com",
            "1000101", "gnruser@account.com",
            "1000201", "cmpnyuser@account.com",
            "1000301", "master@account.com",
            "1000401", "grandmaster@account.com");

    @BeforeEach
    void setUp() {
        msg = new MsgUtil();
        initializer = new DefaultAccountCredentialInitializer(ssmParameterFetcher, recordQueryService, jdbcTemplate,
                passwordEncoder, properties, msg);
    }

    private LinkedHashMap<String, String> row(String password, String mailAddress) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("PASSWORD", password);
        row.put("MAIL_ADDRESS", mailAddress);
        return row;
    }

    /** SELECTの第2引数(ACCNT_IDリスト)に応じて、各アカウントのデフォルト値(シード相当)の行を返すようスタブする */
    private void stubDefaultRowsForAllAccounts() {
        when(recordQueryService.select(anyString(), any())).thenAnswer(invocation -> {
            List<String> params = invocation.getArgument(1);
            String accntId = params.get(0);
            return new ArrayList<>(List.of(
                    row(DefaultAccountCredentialInitializer.DEFAULT_PASSWORD_HASH,
                            DEFAULT_MAIL_ADDRESS_BY_ID.get(accntId))));
        });
    }

    @Test
    void デフォルトのままの全5アカウントのパスワードとメールアドレスがSSMの値で更新されること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        stubDefaultRowsForAllAccounts();
        when(ssmParameterFetcher.fetchSecureString(anyString()))
                .thenReturn(Optional.of("newValue"));
        when(passwordEncoder.encode("newValue")).thenReturn("newHashedPassword");

        initializer.run(applicationArguments);

        // パスワード用・メールアドレス用の両方のパラメータが、5アカウント分取得されること
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/guest/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/guest/mailAddress");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/grandmaster/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/grandmaster/mailAddress");
        // 5アカウント分、PASSWORD・MAIL_ADDRESSの両方を含むUPDATEが実行されること
        verify(jdbcTemplate, times(5)).update(anyString(), eq("newHashedPassword"), eq("newValue"), any(), any(),
                any());
    }

    @Test
    void メールアドレス用SSMパラメータが未設定の場合はパスワードのみ更新されること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        stubDefaultRowsForAllAccounts();
        when(ssmParameterFetcher.fetchSecureString(org.mockito.ArgumentMatchers.contains("/password")))
                .thenReturn(Optional.of("newPlainPassword"));
        when(ssmParameterFetcher.fetchSecureString(org.mockito.ArgumentMatchers.contains("/mailAddress")))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("newPlainPassword")).thenReturn("newHashedPassword");

        initializer.run(applicationArguments);

        // 5アカウント分、PASSWORDのみを含むUPDATEが実行され、メールアドレスはシードデータのまま維持されること
        verify(jdbcTemplate, times(5)).update(anyString(), eq("newHashedPassword"), any(), any(), any());
    }

    @Test
    void 既にパスワード_メールアドレスとも変更済みのアカウントは更新されないこと() {

        when(recordQueryService.select(anyString(), any()))
                .thenReturn(new ArrayList<>(List.of(row("$2a$10$alreadyChangedHash", "real@example.com"))));

        initializer.run(applicationArguments);

        verify(ssmParameterFetcher, never()).fetchSecureString(anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void パスワードは変更済みだがメールアドレスがデフォルトのままの場合はメールアドレスのみ更新されること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        // パスワードは既に変更済みにするため、直接スタブする(先にstubDefaultRowsForAllAccounts()は呼ばない)
        when(recordQueryService.select(anyString(), any())).thenAnswer(invocation -> {
            List<String> params = invocation.getArgument(1);
            String accntId = params.get(0);
            return new ArrayList<>(
                    List.of(row("$2a$10$alreadyChangedHash", DEFAULT_MAIL_ADDRESS_BY_ID.get(accntId))));
        });
        when(ssmParameterFetcher.fetchSecureString(org.mockito.ArgumentMatchers.contains("/mailAddress")))
                .thenReturn(Optional.of("real@example.com"));

        initializer.run(applicationArguments);

        verify(ssmParameterFetcher, never()).fetchSecureString(org.mockito.ArgumentMatchers.contains("/password"));
        verify(jdbcTemplate, times(5)).update(anyString(), eq("real@example.com"), any(), any(), any());
    }

    @Test
    void パスワード用SSMパラメータが未設定の場合は起動を失敗させること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        stubDefaultRowsForAllAccounts();
        when(ssmParameterFetcher.fetchSecureString(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer.run(applicationArguments))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void ACCNTにレコードが存在しない場合は起動を失敗させること() {

        when(recordQueryService.select(anyString(), any())).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> initializer.run(applicationArguments))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void デフォルトパスワードハッシュ定数がACCNT_txtのシード値と一致すること() {

        // ACCNT.txtの値が将来変更された際、本クラスの冪等性判定が追随できず気づかないまま
        // 差し替え処理が常時スキップされてしまう事故を防ぐためのリグレッションテスト
        assertThat(DefaultAccountCredentialInitializer.DEFAULT_PASSWORD_HASH)
                .isEqualTo("$2a$10$w6D8P5pBpmrfJK0c2tKKre9e39qKzUuSwB8WdNyNUHkP8WXacC0fK");
    }
}
