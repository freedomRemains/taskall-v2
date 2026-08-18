package com.freedom.taskall_v2.web.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.web.service.TwoFactorMailService;
import com.freedom.taskall_v2.web.service.TwoFactorMailServiceTestConfig;

/**
 * ローカルプロファイル(デフォルトの有効プロファイル)における認証/認可の挙動を検証するテスト
 * クラスです。ローカルプロファイルではCSRF対策を無効化するため(issue #13)、CSRF対策が有効な
 * 本番相当の挙動は{@link SecurityConfigProdProfileTest}で検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TwoFactorMailServiceTestConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TwoFactorMailService twoFactorMailService;

    @BeforeEach
    void resetMailServiceMock() {

        // テストクラス内で共有されるSpringコンテキストのモックなので、実行順序に依存しないよう毎回呼び出し回数をリセットする
        reset(twoFactorMailService);
    }

    @Test
    void 未認証でもマイページのGETは許可されること() throws Exception {

        mockMvc.perform(get("/taskall-v2/service/myPage.html"))
                .andExpect(status().isOk());
    }

    @Test
    void 未認証でもパスワード再設定画面のGETは許可されること() throws Exception {

        mockMvc.perform(get("/taskall-v2/service/inputMail.html"))
                .andExpect(status().isOk());
    }

    @Test
    void 正しいメールアドレスとパスワードでログインするとマイページへリダイレクトされること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .with(csrf())
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password")
                        .param("g-recaptcha-response", "test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taskall-v2/service/twoFactorAuth.html"));
    }

    @Test
    void 正しいメールアドレスとパスワードでログインすると二段階認証パスコードメールが1回だけ送信されること() throws Exception {

        // GlobalAuthenticationManagerに同じAuthenticationProviderのBeanが重複登録されても
        // 二重に認証処理が実行されない(パスコードメールが2通送信されない)ことを確認する
        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .with(csrf())
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password")
                        .param("g-recaptcha-response", "test"))
                .andExpect(status().is3xxRedirection());

        verify(twoFactorMailService, times(1)).sendPasscode(anyString(), anyString());
    }

    @Test
    void 誤ったパスワードでログインするとエラーメッセージキー付きでマイページへリダイレクトされること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .with(csrf())
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "wrongPassword")
                        .param("g-recaptcha-response", "test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String redirectedUrl = result.getResponse().getRedirectedUrl();
                    org.assertj.core.api.Assertions.assertThat(redirectedUrl)
                            .startsWith("myPage.html?errMsgKey=");
                });
    }

    @Test
    void ログアウトするとマイページへリダイレクトされること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/logout.html").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taskall-v2/service/myPage.html"));
    }

    @Test
    void ローカルプロファイルではCSRFトークンなしでログインしてもリダイレクトされること() throws Exception {

        // issue #13: ローカルプロファイルはデバッグ効率化のためCSRF対策を無効化しているため、
        // CSRFトークン無しのPOSTでも403にならず通常通り処理されることを確認する
        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password")
                        .param("g-recaptcha-response", "test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taskall-v2/service/twoFactorAuth.html"));
    }
}
