package com.freedom.taskall_v2.web.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.taskall_v2.web.service.TwoFactorMailServiceTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TwoFactorMailServiceTestConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 未認証でもマイページのGETは許可されること() throws Exception {

        mockMvc.perform(get("/taskall-v2/service/myPage.html"))
                .andExpect(status().isOk());
    }

    @Test
    void 正しいメールアドレスとパスワードでログインするとマイページへリダイレクトされること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .with(csrf())
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taskall-v2/service/twoFactorAuth.html"));
    }

    @Test
    void 誤ったパスワードでログインするとエラーメッセージキー付きでマイページへリダイレクトされること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .with(csrf())
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "wrongPassword"))
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
    void CSRFトークンなしでログインを試みると403が返却されること() throws Exception {

        mockMvc.perform(post("/taskall-v2/service/myPage.html")
                        .param("MAIL_ADDRESS", "guest@account.com")
                        .param("PASSWORD", "password"))
                .andExpect(status().isForbidden());
    }
}
