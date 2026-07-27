package com.freedom.taskall_v2.common.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.freedom.taskall_v2.common.exception.ApplicationInternalException;
import com.freedom.taskall_v2.common.exception.BusinessRuleViolationException;
import com.freedom.taskall_v2.common.util.MsgUtil;

/**
 * タブ区切り(TSV)形式のテーブル定義／テーブルデータファイルを読み込むクラスです。
 *
 * <p>
 * 1行目をヘッダ行、2行目以降を1レコードとして扱い、{@code ArrayList<LinkedHashMap<String, String>>}
 * （1レコード＝{@code LinkedHashMap}、ヘッダの記述順を維持）で返却します。
 * 空行はスキップします。値が列数より少ない行は、不足分を空文字列として扱います。
 * </p>
 */
public class TsvTableFileReader {

    private final MsgUtil msg;

    public TsvTableFileReader() {
        this(new MsgUtil());
    }

    public TsvTableFileReader(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * TSVファイルを読み込み、レコードのリストを返却します。
     *
     * <p>
     * SQL生成（開発時のファイル生成処理）など、ファイルシステム上のパスを直接扱いたい場合に使用します。
     * </p>
     *
     * @param filePath 読み込むTSVファイルのパス
     * @return 読み込んだレコードのリスト
     */
    public ArrayList<LinkedHashMap<String, String>> read(Path filePath) {

        // ファイルの存在を確認したうえで、共通のInputStream読み込み処理へ委譲する
        if (!Files.exists(filePath)) {
            throw new BusinessRuleViolationException(msg.get("msg.err.common.db.fileNotFound", filePath));
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return read(inputStream);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileReadFailed", filePath), e);
        }
    }

    /**
     * TSVの内容を読み込み、レコードのリストを返却します。
     *
     * <p>
     * クラスパス上のリソース（パッケージされたjar内も含む）を読み込めるよう、
     * {@link InputStream}を受け取る形にしています。
     * </p>
     *
     * @param inputStream 読み込むTSV内容の入力ストリーム（呼び出し側でクローズすること）
     * @return 読み込んだレコードのリスト
     */
    public ArrayList<LinkedHashMap<String, String>> read(InputStream inputStream) {

        // UTF-8のBufferedReaderを作成し、TSVレコード解析の共通処理を呼び出す
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return readRecords(reader);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.tsvReadFailed"), e);
        }
    }

    private ArrayList<LinkedHashMap<String, String>> readRecords(BufferedReader reader) throws IOException {

        // 先頭行をヘッダ行として読み込み、列名一覧を確定する
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new BusinessRuleViolationException(msg.get("msg.err.common.db.headerRowNotFound"));
        }
        String[] headers = headerLine.split("\t", -1);

        // 残りの各行をヘッダ順のレコードへ変換し、列不足分は空文字列で補完する
        ArrayList<LinkedHashMap<String, String>> records = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String[] values = line.split("\t", -1);
            LinkedHashMap<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                record.put(headers[i], i < values.length ? values[i] : "");
            }
            records.add(record);
        }
        return records;
    }
}
