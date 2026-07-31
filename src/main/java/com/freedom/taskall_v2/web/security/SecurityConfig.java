package com.freedom.taskall_v2.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SpringSecurityによる認証(ログイン/ログアウト)のみを設定するクラスです。
 *
 * <p>
 * 認可(画面/画面パーツ単位のread/edit権限)は既存の{@code AuthUtil}が
 * {@code HTML_PARTS_IN_APROLE}を参照して独自に行う仕組みをそのまま維持するため、本クラスでは
 * 全リクエストを{@code permitAll()}とし、SpringSecurity側では認可判定を行いません。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String LOGIN_PAGE_URL = "/taskall-v2/service/myPage.html";

    private final AccountAuthenticationSuccessHandler successHandler;
    private final AccountAuthenticationFailureHandler failureHandler;
    private final TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider;

    public SecurityConfig(AccountAuthenticationSuccessHandler successHandler,
            AccountAuthenticationFailureHandler failureHandler,
            TwoPhaseAuthenticationProvider twoPhaseAuthenticationProvider) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.twoPhaseAuthenticationProvider = twoPhaseAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 認可判定は既存のAuthUtil(HTML_PARTS_IN_APROLE)に委ねるため、SpringSecurity側では
                // 全リクエストを許可する
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // メールアドレス・パスワードの照合およびロック判定・二段階認証の起動は、
                // SpringBoot自動構成のDaoAuthenticationProviderではなく本クラス専用の
                // TwoPhaseAuthenticationProviderへ明示的に委譲する
                .authenticationProvider(twoPhaseAuthenticationProvider)
                // ログイン画面/処理は既存のマイページ(POST)のURLをそのまま流用する
                .formLogin(form -> form
                        .loginPage(LOGIN_PAGE_URL)
                        .loginProcessingUrl(LOGIN_PAGE_URL)
                        .usernameParameter("MAIL_ADDRESS")
                        .passwordParameter("PASSWORD")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                // ログアウトは専用URLを新設し、成功後はマイページへリダイレクトする
                .logout(logout -> logout
                        .logoutUrl("/taskall-v2/service/logout.html")
                        .logoutSuccessUrl(LOGIN_PAGE_URL));

        return http.build();
    }
}
