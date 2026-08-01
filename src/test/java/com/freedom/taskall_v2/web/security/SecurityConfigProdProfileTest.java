package com.freedom.taskall_v2.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.web.service.TwoFactorMailServiceTestConfig;

/**
 * 本番プロファイル(prod)におけるCSRF対策有効化の挙動を検証するテストクラスです。
 *
 * <p>
 * {@code application-prod.yaml}はメール送信先を環境変数({@code TASKALL_MAIL_HOST}等)で
 * 注入する前提のためデフォルト値を持たないが、本テストではCSRF対策の検証にメール送信設定は
 * 不要なので、{@link TestPropertySource}でダミー値を明示的に上書きしてコンテキストの
 * 起動エラーを回避する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "spring.mail.username=dummy",
        "spring.mail.password=dummy"
})
@Import(TwoFactorMailServiceTestConfig.class)
class SecurityConfigProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 本番プロファイルではCSRFトークンなしでログインを試みると403が返却されること() throws Exception {

        // issue #13: CSRF対策の無効化はローカルプロファイル限定であり、本番プロファイルでは
        // 従来通りCSRF対策が有効(トークン無しのPOSTは403)であることを確認する
        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password"))
                .andExpect(status().isForbidden());
    }
}
