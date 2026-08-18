-- issue #80: メール送信を伴う各入口へreCAPTCHA v2チェックボックスを導入する。
-- GNR_KEY_VALへreCAPTCHA失敗時のエラーメッセージを追加し、
-- SCR_ELMへサインアップ/パスワード再設定用のreCAPTCHA検証サービスを追加する。

INSERT INTO GNR_KEY_VAL (GNR_KEY_VAL_ID, GNR_KEY, GNR_VAL, GNR_GRP_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1000410, 'recaptchaVerificationFailed', 'reCAPTCHA認証に失敗しました。もう一度チェックしてください。', 1000401, 10, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');
INSERT INTO SCR_ELM (SCR_ELM_ID, SERVICE_NAME, ADAPTER, PREPARE_INPUT, SCR_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1102051, 'com.freedom.taskall_v2.web.service.RecaptchaVerificationScriptElementService', '', '', 1101651, 1, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');
INSERT INTO SCR_ELM (SCR_ELM_ID, SERVICE_NAME, ADAPTER, PREPARE_INPUT, SCR_ID, ORD_IN_GRP, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) VALUES (1102151, 'com.freedom.taskall_v2.web.service.RecaptchaVerificationScriptElementService', '', '', 1101851, 1, 1, 0, 'data_loader', '2026-08-18 00:00:00', 'data_loader', '2026-08-18 00:00:00');
