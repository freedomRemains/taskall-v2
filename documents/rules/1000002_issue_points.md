# 各issueのポイントまとめ

---

[READMEに戻る](../../README.md)

---

## 概要

本資料では、各issueで対応・議論した内容のうち、今後の開発でも参照価値の高いポイントを、
issue単位で簡潔にまとめます。issueやpull requestの全文を毎回読み返さなくても、
本資料を見れば経緯・結論・関連ファイルが把握できることを目的とします。

- 新しいissueの対応が完了した際は、本資料の末尾に`###`見出しでセクションを追加してください。
- 記述は箇条書きを基本とし、issue URL・PR URL・関連する相対パスを明記してください。
- 本資料はissue #21の対応から運用を開始しています。それ以前のissue（#13, #15, #17, #19等）に
  ついては、必要に応じて別途追記します。

---

### issue #21: messages.propertiesのログレベル／例外種別の規約整理

- issue: https://github.com/freedomRemains/taskall-v2/issues/21
- PR: https://github.com/freedomRemains/taskall-v2/pull/22
- `messages.properties`のメッセージキーは、`msg.err.*`と`msg.warn.*`の2種類の接頭辞を使い分ける。
  - `msg.err.*`: `ApplicationInternalException`をスローし`logger.error`でログ出力する
    （システムが継続動作できない、システム的なエラー）。
  - `msg.warn.*`: `BusinessRuleViolationException`をスローする、または`logger.warn`で
    直接ログ出力する（業務的・入力値的なエラーで、システムは継続動作する）。
- Spring Securityの`AuthenticationProvider`実装（`BadCredentialsException`,
  `LockedException`, 独自の`TwoFactorRequiredException`等）は、Spring Security側の
  認証処理契約上`AuthenticationException`派生でなければならないため、上記規約の例外として
  許容する（`VerifyTwoFactorAuthService.java`等）。
- 本issueで実施した具体的な修正:
  - `msg.err.web.businessRuleViolation` → `msg.warn.web.businessRuleViolation`に改名
    （`GlobalExceptionHandler.java`のログレベルも`logger.error`→`logger.warn`に変更）。
  - `msg.err.common.db.columnDefNotFound`（`CreateTableSqlBuilder.java`,
    `SelectSqlBuilder.java`）、`msg.err.common.db.columnDefForColumnNotFound`
    （`InsertSqlBuilder.java`）、`msg.err.common.db.fileNotFound`／
    `msg.err.common.db.headerRowNotFound`（`TsvTableFileReader.java`）は、
    キー名はそのまま（`msg.err`）とし、スローする例外を`BusinessRuleViolationException`
    から`ApplicationInternalException`に変更（TBL_DEF等の資材データ不整合はシステム
    運用継続不可能なエラーと判断したため）。
  - `msg.err.web.roleRestriction`（`GetAccountService.java`）、
    `msg.err.web.requiredParamMissing`（`GetRelatedRecordService.java`,
    `BulkDeleteRecordService.java`, `UpdateRecordService.java`,
    `CreateRecordService.java`, `VerifyTwoFactorAuthService.java`,
    `DeleteRecordService.java`）、`msg.err.web.invalidTableName`
    （`TableNameValidator.java`）、`msg.err.common.db.tsvValueContainsReservedMarker`
    （`TsvValueEscaper.java`）は、いずれも例外種別（`BusinessRuleViolationException`）は
    正しかったため変更せず、キー名のみ`msg.warn.*`に改名。
- 全体監査は、`messages.properties`の全33キーについて実施し、issueコメントに
  一覧表として結果を掲載した。今後同種の規約整理issueが発生した場合は、この監査結果を
  参考にしてよい。
