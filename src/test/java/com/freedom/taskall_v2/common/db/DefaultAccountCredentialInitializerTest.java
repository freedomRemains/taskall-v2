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

import java.util.LinkedHashMap;
import java.util.List;
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

    @BeforeEach
    void setUp() {
        msg = new MsgUtil();
        initializer = new DefaultAccountCredentialInitializer(ssmParameterFetcher, recordQueryService, jdbcTemplate,
                passwordEncoder, properties, msg);
    }

    private LinkedHashMap<String, String> passwordRow(String password) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("PASSWORD", password);
        return row;
    }

    @Test
    void デフォルトパスワードのままの全5アカウントがSSMの値で更新されること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        when(recordQueryService.select(anyString(), any()))
                .thenReturn(new java.util.ArrayList<>(
                        List.of(passwordRow(DefaultAccountCredentialInitializer.DEFAULT_PASSWORD_HASH))));
        when(ssmParameterFetcher.fetchSecureString(anyString())).thenReturn(Optional.of("newPlainPassword"));
        when(passwordEncoder.encode("newPlainPassword")).thenReturn("newHashedPassword");

        initializer.run(applicationArguments);

        verify(jdbcTemplate, times(5)).update(anyString(), eq("newHashedPassword"), eq("ssm_credential_init"),
                anyString(), anyString());
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/guest/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/individual/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/corporate/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/master/password");
        verify(ssmParameterFetcher).fetchSecureString("/taskall-v2/accnt/grandmaster/password");
    }

    @Test
    void 既にデフォルトパスワードから変更済みのアカウントは更新されないこと() {

        when(recordQueryService.select(anyString(), any()))
                .thenReturn(new java.util.ArrayList<>(List.of(passwordRow("$2a$10$alreadyChangedHash"))));

        initializer.run(applicationArguments);

        verify(ssmParameterFetcher, never()).fetchSecureString(anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void SSMパラメータが未設定の場合は起動を失敗させること() {

        when(properties.getParameterPrefix()).thenReturn("/taskall-v2/accnt");
        when(recordQueryService.select(anyString(), any()))
                .thenReturn(new java.util.ArrayList<>(
                        List.of(passwordRow(DefaultAccountCredentialInitializer.DEFAULT_PASSWORD_HASH))));
        when(ssmParameterFetcher.fetchSecureString(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer.run(applicationArguments))
                .isInstanceOf(ApplicationInternalException.class);
    }

    @Test
    void ACCNTにレコードが存在しない場合は起動を失敗させること() {

        when(recordQueryService.select(anyString(), any())).thenReturn(new java.util.ArrayList<>());

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
