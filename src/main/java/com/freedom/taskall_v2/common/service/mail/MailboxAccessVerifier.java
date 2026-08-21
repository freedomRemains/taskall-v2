package com.freedom.taskall_v2.common.service.mail;

/**
 * 監視対象メールアドレス(issue #96)へ実際に接続し、メールが読める状態かどうかを検証するインターフェースです。
 *
 * <p>
 * メールアドレス登録画面の「登録」ボタン押下時に、入力されたメールアドレス・パスワードで
 * 実際にメールボックスへアクセスできるかどうかを確認するために使用します。認証情報の誤りや
 * 接続不可など、メールが読めない事象は全て業務的な「読めなかった」として扱い、
 * {@link #canAccess(String, String)}は例外を投げずに{@code false}を返却します。
 * </p>
 */
public interface MailboxAccessVerifier {

    /**
     * 指定したメールアドレス・パスワードで、実際にメールボックスへアクセスできるかどうかを確認します。
     *
     * @param mailAddress 監視対象メールアドレス
     * @param password    メールボックスのパスワード
     * @return メールが読める場合は{@code true}、読めない場合は{@code false}
     */
    boolean canAccess(String mailAddress, String password);
}
