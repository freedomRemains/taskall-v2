package com.freedom.taskall_v2.web.security;

import java.util.LinkedHashMap;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.freedom.taskall_v2.web.service.AccntAuthLockService;
import com.freedom.taskall_v2.web.service.LoginStatusService;
import com.freedom.taskall_v2.web.service.TwoFactorMailService;
import com.freedom.taskall_v2.web.util.PasscodeGenerator;

import jakarta.servlet.http.HttpServletRequest;

/**
 * メールアドレス・パスワードによる一次認証を行い、通過した場合は二次認証(6桁パスコード)を
 * 要求する{@link AuthenticationProvider}です。
 *
 * <p>
 * 設計書(documents/design/2000006_two_phase_login.md)「二段階認証処理」節の一次認証手順
 * (アカウント検索→アカウント認証ロック確認→ログイン試行登録→パスワード照合→
 * 成功/失敗時のテーブル更新)をそのまま実装します。一次認証に成功しても、ここでは
 * SpringSecurityの認証成功として扱わず{@link TwoFactorRequiredException}をスローし、
 * 実際のログイン確立は二次認証成功時({@code VerifyTwoFactorAuthService})まで持ち越します。
 * </p>
 */
@Component
public class TwoPhaseAuthenticationProvider implements AuthenticationProvider {

    private final AccountUserDetailsService accountUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AccntAuthLockService accntAuthLockService;
    private final LoginStatusService loginStatusService;
    private final PasscodeGenerator passcodeGenerator;
    private final TwoFactorMailService twoFactorMailService;

    public TwoPhaseAuthenticationProvider(AccountUserDetailsService accountUserDetailsService,
            PasswordEncoder passwordEncoder, AccntAuthLockService accntAuthLockService,
            LoginStatusService loginStatusService, PasscodeGenerator passcodeGenerator,
            TwoFactorMailService twoFactorMailService) {
        this.accountUserDetailsService = accountUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.accntAuthLockService = accntAuthLockService;
        this.loginStatusService = loginStatusService;
        this.passcodeGenerator = passcodeGenerator;
        this.twoFactorMailService = twoFactorMailService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String mailAddress = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();

        // メールアドレスに対応するアカウントが存在しない場合は、以降のテーブル操作を一切行わず
        // 即座に認証失敗とする(アカウント存在有無を推測されないようにするため)
        AccountPrincipal principal;
        try {
            principal = (AccountPrincipal) accountUserDetailsService.loadUserByUsername(mailAddress);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("メールアドレスもしくはパスワードが間違っています。");
        }
        String accountId = principal.getAccountId();

        // アカウント全体がロック中の場合は、このセッションが原因かどうかに関わらず処理を打ち切る
        if (accntAuthLockService.isLocked(accountId)) {
            throw new LockedException("アカウントは現在、ロックされています。");
        }

        // RequestContextHolderから現在のリクエストを取得する
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();

        // このセッション専用のログイン試行行を用意する(有効期限内の既存行があれば引き継ぐ)
        String sessionId = request.getSession().getId();
        LinkedHashMap<String, String> loginStatus = loginStatusService.beginAttempt(accountId, sessionId);
        String loginStatusId = loginStatus.get("LOGIN_STATUS_ID");

        if (passwordEncoder.matches(rawPassword, principal.getPassword())) {
            return handlePasswordSuccess(accountId, mailAddress, loginStatusId, request);
        }
        return handlePasswordFailure(accountId, loginStatusId);
    }

    private Authentication handlePasswordSuccess(String accountId, String mailAddress, String loginStatusId,
            HttpServletRequest request) {

        // 6桁のパスコードを生成し、一方向ハッシュ化した値のみをLOGIN_STATUSへ保存する
        // (平文の6桁数字はDBに保存しない)
        String passcode = passcodeGenerator.generate();
        String passcodeHash = passwordEncoder.encode(passcode);
        loginStatusService.markFirstAuthPass(loginStatusId, passcodeHash);
        accntAuthLockService.resetFailCountOnSuccess(accountId);
        twoFactorMailService.sendPasscode(mailAddress, passcode);

        // 二次認証のPOST/GET処理が参照できるよう、セッションへ一次認証通過中のアカウントIDを記録する
        request.getSession().setAttribute("pendingTwoFactorAccountId", accountId);

        throw new TwoFactorRequiredException("一次認証を通過しました。二段階認証のパスコードを入力してください。");
    }

    private Authentication handlePasswordFailure(String accountId, String loginStatusId) {
        loginStatusService.markFirstAuthFail(loginStatusId);
        accntAuthLockService.recordFailure(accountId);
        throw new BadCredentialsException("メールアドレスもしくはパスワードが間違っています。");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
