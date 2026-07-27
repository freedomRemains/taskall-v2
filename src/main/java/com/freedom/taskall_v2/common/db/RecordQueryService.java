package com.freedom.taskall_v2.common.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * SELECT専用のDBアクセスクラスです。
 *
 * <p>
 * 移植元「remainz」の{@code GenericDb}/{@code DbInterface}に相当する仕組みとして、
 * Spring管理の{@link JdbcTemplate}を利用します。カラムの値は元の型(INT/DATETIME等)に
 * 関わらず一律文字列として扱い、結果を{@code ArrayList<LinkedHashMap<String,String>>}
 * (1レコード=1マップ、カラム順序はSELECT記述順を維持)で返却します。
 * </p>
 *
 * <p>
 * commit/rollbackは呼び出し側(共通処理メソッド)に付与する{@code @Transactional}で
 * Spring管理とするため、本クラスでは明示的なcommit/rollback処理は行いません。
 * </p>
 */
@Service
public class RecordQueryService {

    private final JdbcTemplate jdbcTemplate;

    public RecordQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * バインドパラメータ無しでSELECTを実行します。
     *
     * @param sql SQL文
     * @return 検索結果(1レコード=1マップ、カラム順序はSELECT記述順を維持)
     */
    public ArrayList<LinkedHashMap<String, String>> select(String sql) {
        return select(sql, List.of());
    }

    /**
     * バインドパラメータ付きでSELECTを実行します。
     *
     * @param sql    SQL文({@code ?}でバインドパラメータを表す)
     * @param params バインドパラメータのリスト(SQL文中の{@code ?}の出現順に対応する)
     * @return 検索結果(1レコード=1マップ、カラム順序はSELECT記述順を維持)
     */
    public ArrayList<LinkedHashMap<String, String>> select(String sql, List<String> params) {

        // JdbcTemplateでSELECTを実行し、生の検索結果を取得する
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        // 取得した各行を、カラム順を保った文字列マップの一覧に変換する
        ArrayList<LinkedHashMap<String, String>> resultList = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LinkedHashMap<String, String> columnMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = entry.getValue();
                columnMap.put(entry.getKey(), value == null ? null : value.toString());
            }
            resultList.add(columnMap);
        }
        return resultList;
    }
}
