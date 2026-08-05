package com.freedom.taskall_v2.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
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

    /**
     * {@link TwoPhaseAuthenticationProvider}のみを保持する(親{@link AuthenticationManager}を
     * 持たない){@link ProviderManager}を明示的に生成します。
     *
     * <p>
     * {@code TwoPhaseAuthenticationProvider}が{@code @Component}であるため、SpringBootの
     * 自動構成によって「Global AuthenticationManager」にも同じBeanが登録されてしまいます。
     * {@code HttpSecurity#authenticationProvider}のみを呼び出す実装では、その
     * Global AuthenticationManagerが親としてこのHttpSecurity用のProviderManagerへ設定されるため、
     * 一次認証成功時にスローする{@link TwoFactorRequiredException}
     * (通常の{@link org.springframework.security.core.AuthenticationException}であり
     * {@code AccountStatusException}等ではない)が{@code ProviderManager}に一旦捕捉された後、
     * 認証結果が得られていないと判断され、親であるGlobal AuthenticationManagerで
     * 同じ{@code TwoPhaseAuthenticationProvider}が再度実行されてしまい、パスコードメールが
     * 2通送信される不具合があった。ここで親を持たない{@code ProviderManager}を明示的に
     * {@code HttpSecurity#authenticationManager}へ渡すことで、二重実行を防止する。
     * </p>
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(twoPhaseAuthenticationProvider);
    }

    /**
     * ローカル環境(local プロファイル)ではCSRFトークンの手動埋め込み無しで
     * デバッグ・動作確認を行えるよう、CSRF対策を無効化します。
     */
    @Bean
    @Profile("local")
    public Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizerForLocal() {
        return CsrfConfigurer::disable;
    }

    /**
     * 本番環境(local以外のプロファイル)では、従来通りCSRF対策を有効のまま維持します。
     */
    @Bean
    @Profile("!local")
    public Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizerForProd() {
        return csrf -> {
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
            Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer)
            throws Exception {

        http
                // ローカル環境のみCSRF対策を無効化する(csrfCustomizerForLocal/csrfCustomizerForProdは
                // アクティブなプロファイルに応じてどちらか一方のみBean登録される)
                .csrf(csrfCustomizer)
                // 認可判定は既存のAuthUtil(HTML_PARTS_IN_APROLE)に委ねるため、SpringSecurity側では
                // 全リクエストを許可する
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // メールアドレス・パスワードの照合およびロック判定・二段階認証の起動は、
                // SpringBoot自動構成のDaoAuthenticationProviderではなく本クラス専用の
                // TwoPhaseAuthenticationProviderへ明示的に委譲する。親を持たない上記の
                // AuthenticationManagerを明示的に指定することで、TwoPhaseAuthenticationProviderが
                // 二重に実行されることを防ぐ
                .authenticationManager(authenticationManager)
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
