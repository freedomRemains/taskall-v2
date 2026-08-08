package com.freedom.taskall_v2.common.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

@ExtendWith(MockitoExtension.class)
class AwsSsmParameterFetcherTest {

    @Mock
    private SsmClient ssmClient;

    @InjectMocks
    private AwsSsmParameterFetcher fetcher;

    @Test
    void パラメータが存在する場合は復号済みの値を返却すること() {

        Parameter parameter = Parameter.builder().value("plainValue").build();
        GetParameterResponse response = GetParameterResponse.builder().parameter(parameter).build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        Optional<String> result = fetcher.fetchSecureString("/taskallv2/accnt/grandmaster/password");

        assertThat(result).contains("plainValue");
    }

    @Test
    void パラメータが存在しない場合は空を返却すること() {

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("not found").build());

        Optional<String> result = fetcher.fetchSecureString("/taskallv2/accnt/grandmaster/password");

        assertThat(result).isEmpty();
    }
}
