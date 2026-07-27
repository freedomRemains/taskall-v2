# taskall-v2プロジェクト立ち上げ 設計書

---

## 背景・目的

taskall-v2で移植元「remainz」の機能をほぼ移植できたため、taskall-v2自体のツール拡張開発は
一旦中止する。代わりに、taskall-v2の仕組み(エンジン)を使い、既存システム「助か～る」
(https://www.taskall.co.jp/ankeninfo/index.jsp) を再構築するプロジェクト
「taskall-v2」を、taskall-v2から完全に独立した別管理プロジェクトとして立ち上げる。

本設計書は、taskall-v2を実際に構築・運用開始するまでの一連の作業のうち、
**最初のステップ「taskall-v2資材のコピーによるtaskall-v2プロジェクト立ち上げ」**
のみを対象とする。以下は明確にスコープ外とし、別途相談・設計する。

- IaC(terraform)によるAWS環境構築
- GitHub ActionsによるCI/CD自動デプロイ
- 「助か～る」の個別機能移植issue作成・実装

## 前提・制約

- 「助か～る」の既存ソースコードは参照できない。remainzのようなローカルクローンは存在しない。
- そのため「助か～る」の移植は、画面を実機で確認しながらissueに機能概要を記述し、
  新規実装として作り直す方針とする。元ソースの完全移植ではない。
- 画面外観は現代的な一般アプリの見た目に刷新し、レスポンシブ対応を行う。
- 機能は既存機能の一部引き継ぎに加え、大幅な改修・増強を行う想定。

## 採用するアプローチ

taskall-v2の作業ツリーを`.git`を除いてローカルコピーし、`remainz`系の命名を
`taskall`系へスクリプトで一括置換した上で、GitHub上に新規作成した空リポジトリへ
最初のコミットとしてpushする。

比較した他アプローチ:

- GitHubの「Use this template」機能: UIでの複製は容易だが、結局ファイル内の文字列
  置換は別途手作業が必要で手間は同程度。テンプレート化という余計な設定が残るため不採用。
- スキャフォールディングツール(cookiecutter等)の導入: 今回は一回限りの複製であり、
  繰り返し複製する予定がないため投資対効果が低く不採用。

## 手順詳細

### ① リポジトリ作成

- GitHub organization `freedomRemains` 配下に、空リポジトリ `taskall-v2` を新規作成する
  (taskall-v2のfork/importは使わない。コミット履歴は引き継がない)。
- ブランチ運用はtaskall-v2と同じ規則を踏襲する
  (`main` ← `develop` ← `feature/<issue番号>`、PRは`feature/<issue番号>`から`develop`宛て)。
- ローカルに`taskall-v2`と並列で`taskall-v2`ディレクトリを新規作成し、`.git`を除いた
  作業ツリー一式(`src/`, `documents/`, `build.gradle`等)をコピーする。
- コピー後`git init`し、`taskall-v2`のGitHubリポジトリをoriginとして設定、初回コミットとして
  push する。

### ② コピー＆リネーム対象

taskall-v2のリポジトリ内には現状102ファイルに「remainz」関連の記述がある。以下の対象を
スクリプトで一括置換する。

| 対象 | 変更前 | 変更後 |
| --- | --- | --- |
| Javaパッケージ | `com.freedom.remainz_v2` (ディレクトリ含む) | `com.freedom.taskall_v2` |
| Gradleプロジェクト名 | `settings.gradle`の`rootProject.name = 'taskall-v2'` | `taskall-v2` |
| アプリ名 | `application.yaml`の`spring.application.name: taskall-v2` | `taskall-v2` |
| URLパスマッピング | `/taskall-v2/service/...` | `/taskall-v2/service/...` |
| GitHubリンク | `github.com/freedomRemains/taskall-v2` | `github.com/freedomRemains/taskall-v2` |
| クラス名 | `RemainzV2Controller`, `RemainzV2ApplicationTests` 等 | `TaskallV2Controller` 等 |
| 環境変数名 | `REMAINZ_DATASOURCE_URL` | `TASKALL_DATASOURCE_URL` |

- DBファイル名は現状のtaskall-v2側で既に`taskallv2.db`となっている(将来taskall-v2を
  見越した命名と思われる)ため、taskall-v2でもそのまま`taskallv2.db`を使用し変更しない。
- 置換実行後、`grep -ri remainz`で置換漏れがないことを確認する。

### ③ ドキュメント調整

| ドキュメント | 対応方針 |
| --- | --- |
| `.github/copilot-instructions.md` | プロジェクト名を`taskall-v2`に置換。「移植元プロジェクトについて」章は、ソース非公開の「助か～る」向けに書き換える(ローカルクローン参照ではなく、issueに画面・機能概要を記述して新規実装する方針、外観は現代的・レスポンシブに刷新、機能は取捨選択・増強する旨を明記)。エンジン自体の設計方針(パッケージ構成、例外方針、DBアクセス方針、採番規則等)は同じエンジンを使うためそのまま踏襲する。 |
| `documents/design/*` (base/model/implementation/model_driven_web design) | taskall-v2エンジンの汎用設計書のためそのままコピーする。ただし「移植元プロジェクト」への言及やissue番号等taskall-v2固有の履歴的記述は、taskall-v2の文脈に合わないため削除・一般化する。 |
| `documents/guideline/4000001_howToUse.md` | エンジンの使い方ガイドとしてそのまま有効なためコピーし、URL例など`taskall-v2`表記のみ`taskall-v2`に置換する。 |
| `documents/prompts/`, `documents/rules/` | taskall-v2の開発履歴(過去のAI回答)はコピーせず、フォルダ構造だけ残して中身は空(READMEのみ)にする。 |
| `README.md` | プロジェクト名・リンクを`taskall-v2`に置換する。「概要」章のAI駆動開発の運用方針はそのまま踏襲する。 |

### ④ 検証手順

1. リネーム後、`grep -ri remainz`で置換漏れがないことを確認する。
2. `./gradlew build`でコンパイル・全テストが通ることを確認する。
3. アプリを起動し、`http://localhost:8080/taskall-v2/service/top.html`で画面表示・
   DBメンテナンス画面が問題なく動作することを確認する
   (初期データがtaskall-v2の5権限のままで良いか、この場でtaskall向けに調整するかは
   別issueとする)。
4. GitHubへの初回push後、GitHub Actions等何も設定されていない状態であることを確認する
   (CI/CDは次フェーズで対応)。

## スコープ外事項(別途相談・設計)

- IaC(terraform)によるAWS環境構築(セキュリティを考慮した構成の協議を含む)
- GitHub ActionsによるCI/CD自動デプロイ
- 「助か～る」の個別機能移植issue作成・実装
