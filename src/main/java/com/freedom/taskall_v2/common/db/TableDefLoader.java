package com.freedom.taskall_v2.common.db;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 「TBL_DEF」のデータ（{@code TBL_DEF.txt}）を読み込み、テーブルごとのカラム定義に分類するクラスです。
 *
 * <p>
 * {@code TBL_DEF.txt}には全テーブル分のカラム定義行がまとめて記述されているため、
 * {@code TABLE_NAME}カラムの値ごとにグルーピングして返却します。
 * </p>
 */
public class TableDefLoader {

    private final TsvTableFileReader tsvTableFileReader;

    public TableDefLoader() {
        this(new TsvTableFileReader());
    }

    public TableDefLoader(TsvTableFileReader tsvTableFileReader) {
        this.tsvTableFileReader = tsvTableFileReader;
    }

    /**
     * TBL_DEF.txtを読み込み、テーブル名ごとのカラム定義に分類して返却します。
     *
     * @param tblDefFilePath TBL_DEF.txtのパス
     * @return テーブル名をキー、カラム定義行のリストを値とするマップ（テーブルの出現順を維持）
     */
    public LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> load(Path tblDefFilePath) {
        return groupByTableName(tsvTableFileReader.read(tblDefFilePath));
    }

    /**
     * TBL_DEF.txtの内容を読み込み、テーブル名ごとのカラム定義に分類して返却します。
     *
     * <p>
     * クラスパス上のリソース（パッケージされたjar内も含む）を読み込みたい場合に使用します。
     * </p>
     *
     * @param inputStream TBL_DEF.txtの内容の入力ストリーム（呼び出し側でクローズすること）
     * @return テーブル名をキー、カラム定義行のリストを値とするマップ（テーブルの出現順を維持）
     */
    public LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> load(InputStream inputStream) {
        return groupByTableName(tsvTableFileReader.read(inputStream));
    }

    private LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> groupByTableName(
            ArrayList<LinkedHashMap<String, String>> allRows) {

        // テーブルの出現順を維持できるよう、格納先にはLinkedHashMapを使用します。
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, String>>> tableDefMap = new LinkedHashMap<>();

        // 全行をTABLE_NAMEごとに振り分け、各テーブルのカラム定義リストを順番通りにまとめます。
        for (LinkedHashMap<String, String> row : allRows) {
            String tableName = row.get("TABLE_NAME");
            tableDefMap.computeIfAbsent(tableName, key -> new ArrayList<>()).add(row);
        }
        return tableDefMap;
    }
}
