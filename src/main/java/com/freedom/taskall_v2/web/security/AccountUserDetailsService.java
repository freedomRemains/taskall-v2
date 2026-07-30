package com.freedom.taskall_v2.web.security;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.freedom.taskall_v2.common.db.RecordQueryService;

/**
 * メールアドレスをユーザ名として{@code ACCNT}テーブルからアカウントを検索する
 * {@link UserDetailsService}です。
 *
 * <p>
 * 移植元「remainz」相当の独自認証(旧{@code LoginService})に代わり、SpringSecurityの
 * {@code DaoAuthenticationProvider}経由でBCryptパスワード照合まで委譲するために使用します。
 * </p>
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private static final String ACCOUNT_SQL = """
            SELECT A.ACCNT_ID, A.MAIL_ADDRESS, A.PASSWORD
            FROM ACCNT A
            WHERE A.MAIL_ADDRESS = ?
            """;

    private final RecordQueryService recordQueryService;

    public AccountUserDetailsService(RecordQueryService recordQueryService) {
        this.recordQueryService = recordQueryService;
    }

    @Override
    public UserDetails loadUserByUsername(String mailAddress) {

        // メールアドレスに対応するアカウントが一意に定まらない場合は認証失敗として扱う
        List<LinkedHashMap<String, String>> accountRows =
                recordQueryService.select(ACCOUNT_SQL, List.of(mailAddress));
        if (accountRows.size() != 1) {
            throw new UsernameNotFoundException(mailAddress);
        }

        LinkedHashMap<String, String> account = accountRows.get(0);
        return new AccountPrincipal(account.get("ACCNT_ID"), account.get("MAIL_ADDRESS"), account.get("PASSWORD"));
    }
}
