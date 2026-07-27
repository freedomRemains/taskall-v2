---
# マークダウンでシーケンス図を記述する方法

[READMEに戻る](../../README.md)

- VSCodeの場合は「Markdown Preview Enhanced」という拡張機能をインストールする。
- バッククオート3文字に続けて「mermaid」と記述すると、シーケンス図を記述できる。  

サンプル記述は、次のシーケンス図を参照のこと。

```mermaid
sequenceDiagram
    コック ->> フライパン: ハンバーグを焼く
    フライパン -->> コック : 焼き上がり

```

- mermaidの記法についてはネット調査のこと。
- 「Markdown Preview Enhanced」は右クリックでHTMLやPDFへの出力も可能。
