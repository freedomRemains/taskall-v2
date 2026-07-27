# 実装設計

---

[READMEに戻る](../../README.md)

---

## AIに実装を依頼する場合の前提事項

- GitHub上にissueを作成し、AIに依頼する内容を記述します。
- AIに依頼するときは、そのissueのURLを指定します。
    - 「次のURLで示されるissueの内容を実装してください。[URL]」といった依頼を行うものとします。
    - issue指定のない実装依頼は、「実装依頼にはissueが必要です」と回答してください。
    - その場合、実装作業は行わないものとします。
- 実装作業を開始する前に、superpowers(https://github.com/obra/superpowers)が有効かどうか確認してください。
    - superpowersプラグイン由来のスキル群(using-superpowers、brainstorming、test-driven-development、systematic-debugging、writing-plans)が利用可能なスキルとして読み込まれていることを確認してください。
- AI実装結果は、pull requestとするものと前提します。
    - pull requestの内容をレビューし、指摘があればpull requestにコメントを追記します。
    - pull requestのURLを指定して、指摘修正をAIに依頼するものとします。
        - 「次のURLで示されるpull requestに指摘を追記しました。対応してください。[URL]」といった依頼を行うものとします。

---

## 実装規則

- URLとして示されるGitHub上のissueを確認してください。
- feature/[issue番号]というフィーチャーブランチを作成してください。

| issue番号            | フィーチャーブランチ |
| -------------------- | -------------------- |
| 1                    | feature/1            |
| 2                    | feature/2            |
| ...                  | ...                  |

- 実装には必ずテストを付けることとします。
    - テストクラスがペアになっていない実装はNGです。
    - 実装に対応するテストが存在することを必ず確認してください。
- 実装上、不明点がある場合は、必ず質問してください。
    - 人間の書き間違いや修正ミスにより、意味が通らないissue記載になることが考えられます。
    - 他にも論理自体が破綻している設計などがissueに記載される可能性もありえます。
    - そのような場合は仕様を自律的に訂正・補完せず、必ず質問してください。
- 実装成果物をfeature/[issue番号]にcommit&pushしてください。
- feature/[issue番号] -> developへのpull requestを作成してください。
- 最終的にpull requestのURLを成果物として、実装完了を知らせてください。
- pull requestの指摘があれば、適宜、該当するpull requestのコメントを確認して対応してください。
    - この場合も、不明点がある場合は必ず質問してください。
    - 実装修正の他、テストの修正もお願いします。

---

[READMEに戻る](../../README.md)

---
