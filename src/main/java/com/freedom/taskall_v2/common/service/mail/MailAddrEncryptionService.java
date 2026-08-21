package com.freedom.taskall_v2.common.service.mail;

/**
 * 監視対象メールアドレス(issue #96)のパスワードを、可逆な形式で暗号化・復号するインターフェースです。
 *
 * <p>
 * ログインパスワード({@code ACCNT.PASSWORD})とは異なり、監視対象メールアドレスのパスワードは
 * 毎日午前3時のメール解析バッチで実際にメールボックスへログインするために復号可能な形で
 * 保持する必要があるため、BCrypt等の不可逆ハッシュではなく、本インターフェースによる
 * エンベロープ暗号化を用いる。
 * </p>
 */
public interface MailAddrEncryptionService {

    /**
     * 平文を暗号化します。
     *
     * @param plainText 平文
     * @return 暗号化後の文字列(DBの{@code PASSWORD_ENC}列へそのまま保存できる形式)
     */
    String encrypt(String plainText);

    /**
     * {@link #encrypt(String)}で暗号化した文字列を復号します。
     *
     * @param encryptedText 暗号化済み文字列
     * @return 復号後の平文
     */
    String decrypt(String encryptedText);
}
