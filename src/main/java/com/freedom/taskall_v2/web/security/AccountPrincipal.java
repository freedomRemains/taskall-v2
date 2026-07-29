package com.freedom.taskall_v2.web.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 * 認証済みアカウントを表すプリンシパルです。
 *
 * <p>
 * 権限判定(read/edit)は既存の{@code AuthUtil}が{@code HTML_PARTS_IN_APROLE}を参照して独自に
 * 行うため、SpringSecurity側の認可機構(権限リスト)は使用せず、常に空
 * ({@link AuthorityUtils#NO_AUTHORITIES})とします。
 * </p>
 */
public class AccountPrincipal extends User {

    private static final long serialVersionUID = 1L;

    private final String accountId;

    public AccountPrincipal(String accountId, String mailAddress, String password) {
        super(mailAddress, password, AuthorityUtils.NO_AUTHORITIES);
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }
}
