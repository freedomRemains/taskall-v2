-- issue #78「サインアップ機能の実装」フォローアップ: サインアップ完了時の通知表示対応。
-- 既存の「NTC」テーブル(Flyway導入以前から存在)・「GNR_GRP」/「GNR_KEY_VAL」(汎用通知グループ)
-- ・「通知表示領域」(HTML_PARTS_ID=1000701)の仕組みをそのまま利用し、新規テーブル追加は行わない。
-- TOP画面(HTML_PAGE_ID=1000001)へ通知表示領域を追加し、NTCから通知メッセージを取得する
-- PARTS_ITEMクエリ、及びサインアップ完了メッセージのGNR_KEY_VALマスタデータのみを追加する。

-- GNR_KEY_VAL: サインアップ完了通知メッセージを「汎用通知」グループ(GNR_GRP_ID=1000101)へ追加する。
INSERT INTO GNR_KEY_VAL (GNR_KEY_VAL_ID, GNR_KEY, GNR_VAL, GNR_GRP_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1000106, 'signUpCompleteNotice', 'サインアップ完了しました。<br />お手数ですが、サインインをお願いします。', 1000101, 3, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');

-- PARTS_IN_PAGE: TOP画面(HTML_PAGE_ID=1000001)へ、既存の「通知表示領域」(HTML_PARTS_ID=1000701)を追加する。
-- 対象パーツの画面部品権限(HTML_PARTS_IN_APROLE)は全ロールに既存の読み取り権限が設定済みのため、
-- 本マイグレーションでの追加は不要。
INSERT INTO PARTS_IN_PAGE (PARTS_IN_PAGE_ID, HTML_PAGE_ID, HTML_PARTS_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1000003, 1000001, 1000701, 3, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');

-- PARTS_ITEM: NTCから、リクエストの#{noticeKey}に対応する通知メッセージを取得する。
-- 既存の20080_commonNoticeList.htmlテンプレートがGNR_VAL列名を参照するため、列名をAS句で揃える。
INSERT INTO PARTS_ITEM (PARTS_ITEM_ID, ITEM_KEY, ITEM_QUERY, PARTS_IN_PAGE_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1000003, 'noticeList', 'SELECT NOTICE_MSG AS GNR_VAL FROM NTC WHERE NTC_ID = #{noticeKey}', 1000003, 1, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');
