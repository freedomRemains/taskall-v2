package com.freedom.taskall_v2.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;

/**
 * メッセージキーと可変パラメータから、メッセージを取得するユーティリティクラスです。
 *
 * <p>
 * メッセージは{@code src/main/resources/msg/messages.properties}に記述します。
 * 呼び出し側では{@code private final MsgUtil msg;}のようにDIし、
 * {@code msg.get(key, param1, param2...)}の形式で呼び出します。
 * </p>
 */
@Component
public class MsgUtil {

    private static final String MESSAGE_FILE = "msg/messages.properties";

    private final Properties properties;

    public MsgUtil() {
        this.properties = loadProperties();
    }

    /**
     * キーが示すメッセージを取得し、可変引数を織り込んだ文字列を返却します。
     *
     * @param key  メッセージキー
     * @param args 可変引数
     * @return 可変引数を織り込んだメッセージ
     */
    public String get(String key, Object... args) {
        // メッセージキーに対応する定義を取得し、未定義キーは内部エラーとして扱う。
        String pattern = properties.getProperty(key);
        if (pattern == null) {
            throw new ApplicationInternalException("メッセージキーが見つかりません。key=" + key);
        }
        return MessageFormat.format(pattern, args);
    }

    private Properties loadProperties() {
        // クラスパス上のメッセージ定義ファイルをUTF-8で読み込み、Propertiesへ展開する。
        Properties props = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(MESSAGE_FILE)) {
            if (inputStream == null) {
                throw new ApplicationInternalException("メッセージファイルが見つかりません。file=" + MESSAGE_FILE);
            }
            props.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // メッセージ基盤の初期化に失敗した場合は、起動継続不能な内部エラーとして扱う。
            throw new ApplicationInternalException("メッセージファイルの読み込みに失敗しました。file=" + MESSAGE_FILE, e);
        }
        return props;
    }
}
