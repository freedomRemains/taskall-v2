package com.freedom.taskall_v2.common.db;

import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * TSVファイルへの書き込み前／読み込み後に、各TSV値に対して行う変換処理をまとめたクラスです。
 *
 * <p>
 * TSVはタブ区切り・改行区切りの形式であるため、値自体にCR/LF/タブが含まれると
 * パースが破綻します。一般的にはダブルクオートで囲んでエスケープしますが、当該アプリでは
 * EXCEL貼り付けやテキストエディタでの閲覧性を優先し、CR/LF/タブをそれぞれ独自のマーカー文字列
 * （{@link #CR_MARKER}/{@link #LF_MARKER}/{@link #TAB_MARKER}）へ変換する方式を採用します。
 * </p>
 *
 * <p>
 * 変換前の値に、変換後と同じ文字列（マーカー文字列）が既に含まれている場合、読み込み時に
 * 元の値へ復元できなくなってしまうため、業務エラーとして扱います。
 * </p>
 */
public class TsvValueEscaper {

    static final String CR_MARKER = "#Yr#";
    static final String LF_MARKER = "#Yn#";
    static final String TAB_MARKER = "#Yt#";

    private final MsgUtil msg;

    public TsvValueEscaper() {
        this(new MsgUtil());
    }

    public TsvValueEscaper(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * TSVファイルへの書き込み前に、値に含まれるCR/LF/タブをマーカー文字列へ変換します。
     *
     * @param value 変換対象の値
     * @return 変換後の値
     * @throws BusinessRuleViolationException 変換前の値に、既にマーカー文字列が含まれる場合
     */
    public String encode(String value) {

        // マーカー文字列が既に含まれていると、読み込み時に元の値を復元できなくなるため業務エラーとする
        if (value.contains(CR_MARKER) || value.contains(LF_MARKER) || value.contains(TAB_MARKER)) {
            throw new BusinessRuleViolationException(
                    msg.get("msg.err.common.db.tsvValueContainsReservedMarker", value));
        }

        return value.replace("\r", CR_MARKER).replace("\n", LF_MARKER).replace("\t", TAB_MARKER);
    }

    /**
     * TSVファイルの読み込み後に、マーカー文字列を元のCR/LF/タブへ変換します。
     *
     * @param value 変換対象の値
     * @return 変換後の値
     */
    public String decode(String value) {
        return value.replace(CR_MARKER, "\r").replace(LF_MARKER, "\n").replace(TAB_MARKER, "\t");
    }
}
