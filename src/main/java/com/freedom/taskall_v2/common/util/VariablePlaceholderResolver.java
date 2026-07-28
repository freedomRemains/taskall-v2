package com.freedom.taskall_v2.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

/**
 * {@code #{key}}形式のプレースホルダーを、JSONコンテキストの値で置換するユーティリティクラスです。
 *
 * <p>
 * 移植元「remainz」の{@code ScriptService#convertVariable}に相当する処理です。
 * コンテキストに対応する値が存在しない場合は、警告ログを出力したうえでプレースホルダーを
 * そのまま残します。
 * </p>
 */
public final class VariablePlaceholderResolver {

    private static final Logger logger = LoggerFactory.getLogger(VariablePlaceholderResolver.class);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([0-9a-zA-Z_]+)\\}");

    private VariablePlaceholderResolver() {
    }

    /**
     * テンプレート文字列内の{@code #{key}}形式のプレースホルダーを、コンテキストの値で置換します。
     *
     * @param template テンプレート文字列(nullの場合はnullをそのまま返却する)
     * @param context  プレースホルダーの置換に使用するJSONコンテキスト
     * @param msg      警告ログメッセージの取得に使用する{@link MsgUtil}
     * @return 置換後の文字列
     */
    public static String resolve(String template, JsonNode context, MsgUtil msg) {

        if (template == null) {
            return null;
        }

        // テンプレート文字列を走査するための状態を初期化し、置換結果の組み立てを開始する。
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {

            // 現在のプレースホルダーに対応するキーを取得し、その直前までの固定文字列を連結する。
            String key = matcher.group(1);
            JsonNode valueNode = context.get(key);

            result.append(template, lastEnd, matcher.start());

            // コンテキスト値が無い場合は警告を残してプレースホルダーを維持し、ある場合だけ実値へ置換する。
            if (valueNode == null || valueNode.isNull() || valueNode.asString().isEmpty()) {
                logger.warn(msg.get("msg.warn.web.placeholderValueNotFound", key));
                result.append(matcher.group());
            } else {
                result.append(valueNode.asString());
            }

            lastEnd = matcher.end();
        }
        // 最後のプレースホルダー以降に残った固定文字列を連結し、完成した文字列を返却する。
        result.append(template.substring(lastEnd));

        return result.toString();
    }
}
