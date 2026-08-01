package com.freedom.taskall_v2.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * パスワードエンコーダの設定クラスです。
 *
 * <p>
 * {@link PasswordEncoder}を{@link SecurityConfig}とは独立した{@code @Configuration}クラスとして
 * 定義することで、認証プロバイダ({@link TwoPhaseAuthenticationProvider})との循環依存を回避します。
 * </p>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
