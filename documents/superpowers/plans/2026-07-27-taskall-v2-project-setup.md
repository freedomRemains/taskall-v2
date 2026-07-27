# taskall-v2プロジェクト立ち上げ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** taskall-v2の資材をコピーし、命名・ドキュメントを調整した上で、GitHub organization
`freedomRemains`配下に完全新規・別管理の`taskall-v2`リポジトリとして立ち上げる。

**Architecture:** taskall-v2の作業ツリーを`.git`を除いてローカルコピーし、`remainz`系の命名を
`taskall`系へ一括置換した後、新規リポジトリとして初回コミット・pushする。既存taskall-v2は
一切変更しない(読み取り専用の参照元として扱う)。

**Tech Stack:** Java 21 / Spring Boot / Gradle / Thymeleaf / SQLite (taskall-v2と同一のまま)

## Global Constraints

- 参照元`/home/develop/taskall-v2`は一切変更しない(コピー元として読むだけ)。
- コピー先は`/home/develop/taskall-v2`とする。
- Javaパッケージは`com.freedom.remainz_v2` → `com.freedom.taskall_v2`にリネームする。
- Gradleプロジェクト名・アプリ名・URLマッピング・クラス名は`taskall-v2`/`remainz_v2`/`RemainzV2`
  → `taskall-v2`/`taskall_v2`/`TaskallV2`へ一括置換する。
- DBファイル名`taskallv2.db`は変更しない(既存taskall-v2側で既にこの名称のため)。
- 環境変数名`REMAINZ_DATASOURCE_URL` → `TASKALL_DATASOURCE_URL`に変更する。
- `documents/prompts/`, `documents/rules/`, `.superpowers/sdd/`配下の中身(taskall-v2の開発履歴)
  はコピーしない。フォルダ構造のみ残し、`documents/prompts/`, `documents/rules/`は
  README.mdのみ配置する。
- GitHub organization `freedomRemains`配下に新規リポジトリ`taskall-v2`を作成する
  (fork/importではなく空リポジトリ、コミット履歴は引き継がない)。
- ブランチ運用はtaskall-v2と同じ規則(`main` ← `develop` ← `feature/<issue番号>`)を踏襲する。

---

### Task 1: GitHub上にtaskall-v2リポジトリを作成する(手動)

**Files:** なし(GitHub上の操作のみ)

このタスクはCLIから自動化できない(この環境に`gh` CLIが未インストールかつ認証情報もないため)。
リポジトリ管理者(ユーザ)が以下を手動で実施する。

- [ ] **Step 1: GitHub上で空リポジトリを作成**

https://github.com/organizations/freedomRemains/repositories/new にアクセスし、以下の設定で
作成する。

- Owner: `freedomRemains`
- Repository name: `taskall-v2`
- Description: `project for taskall ver 2.`
- Visibility: taskall-v2と同じ設定(Private/Publicはtaskall-v2の現状設定に合わせる)
- 「Initialize this repository with」配下の README/.gitignore/license は**すべてチェックしない**
  (空リポジトリとして作成し、後続タスクでローカルの完全な資材一式をpushするため)。

- [ ] **Step 2: developブランチ保護ルールの設定(任意、taskall-v2と揃える場合)**

taskall-v2の`develop`ブランチにはPR必須のルールが設定されている
(`Settings > Rules > Rulesets`で確認できる)。taskall-v2でも同様のルールを設定する場合は
GitHub上のSettingsから同じ内容を設定する。

- [ ] **Step 3: 作成確認**

`https://github.com/freedomRemains/taskall-v2` にアクセスし、空リポジトリ(コミット0件)が
表示されることを確認する。

---

### Task 2: ローカル作業ツリーを複製する

**Files:**
- Create: `/home/develop/taskall-v2/` (taskall-v2の作業ツリーのコピー)

**Interfaces:**
- Consumes: `/home/develop/taskall-v2`の現行作業ツリー(変更しない)
- Produces: `/home/develop/taskall-v2`配下に、`.git`・ビルド生成物・taskall-v2固有の
  開発履歴を除いた作業ツリー一式

- [ ] **Step 1: コピー先ディレクトリを作成し、rsyncで複製する**

```bash
mkdir -p /home/develop/taskall-v2
rsync -a --exclude='.git/' --exclude='build/' --exclude='.gradle/' \
  --exclude='.superpowers/sdd/' \
  /home/develop/taskall-v2/ /home/develop/taskall-v2/
```

- [ ] **Step 2: コピーできたことを確認する**

```bash
ls /home/develop/taskall-v2
test -d /home/develop/taskall-v2/.git && echo "NG: .git exists" || echo "OK: no .git"
test -d /home/develop/taskall-v2/.superpowers/sdd && echo "NG: sdd exists" || echo "OK: no sdd"
```

Expected: `OK: no .git` と `OK: no sdd` の両方が出力される。

- [ ] **Step 3: documents/prompts, documents/rules の中身を空にする**

taskall-v2の開発履歴(過去のAI回答)はtaskall-v2に引き継がない。フォルダ構成のみ残し、
既存ファイルは削除の上、READMEのみ再作成する。

```bash
cd /home/develop/taskall-v2
find documents/prompts -type f
find documents/rules -type f
```

上記コマンドの出力に含まれる各ファイルの内容を確認し、`README.md`という名前のファイルが
既に説明用として存在する場合はそれを残し、それ以外の履歴ファイル(プロンプト内容・AI回答)は
削除する。

```bash
cd /home/develop/taskall-v2
find documents/prompts -type f ! -name 'README.md' -delete
find documents/rules -type f ! -name 'README.md' -delete
```

- [ ] **Step 4: 削除後、空でないことを確認(README.mdが残っていること)**

```bash
ls documents/prompts documents/rules
```

Expected: 各フォルダに`README.md`のみ、もしくは0件(taskall-v2側にREADMEが無ければ後続タスクで
新規作成する)。

---

### Task 3: Javaパッケージをリネームする(remainz_v2 → taskall_v2)

**Files:**
- Modify: `src/main/java/com/freedom/remainz_v2/**` → `src/main/java/com/freedom/taskall_v2/**`
- Modify: `src/test/java/com/freedom/remainz_v2/**` → `src/test/java/com/freedom/taskall_v2/**`

**Interfaces:**
- Consumes: Task 2で複製した`/home/develop/taskall-v2`の作業ツリー
- Produces: `com.freedom.taskall_v2`パッケージ配下に移動した全Javaソース(パッケージ宣言・import文も
  書き換え済み)

- [ ] **Step 1: ディレクトリを移動する**

```bash
cd /home/develop/taskall-v2
mkdir -p src/main/java/com/freedom/taskall_v2
mv src/main/java/com/freedom/remainz_v2/* src/main/java/com/freedom/taskall_v2/
rmdir src/main/java/com/freedom/remainz_v2

mkdir -p src/test/java/com/freedom/taskall_v2
mv src/test/java/com/freedom/remainz_v2/* src/test/java/com/freedom/taskall_v2/
rmdir src/test/java/com/freedom/remainz_v2
```

- [ ] **Step 2: パッケージ宣言・import文中の`remainz_v2`を`taskall_v2`に置換する**

```bash
cd /home/develop/taskall-v2
grep -rl 'com\.freedom\.remainz_v2' src | xargs sed -i 's/com\.freedom\.remainz_v2/com.freedom.taskall_v2/g'
```

- [ ] **Step 3: 置換漏れがないことを確認する**

```bash
cd /home/develop/taskall-v2
grep -rn 'remainz_v2' src
```

Expected: 出力なし(何も表示されない)。

- [ ] **Step 4: クラス名をリネームする(RemainzV2 → TaskallV2)**

```bash
cd /home/develop/taskall-v2
mv src/main/java/com/freedom/taskall_v2/RemainzV2Application.java \
   src/main/java/com/freedom/taskall_v2/TaskallV2Application.java
mv src/main/java/com/freedom/taskall_v2/web/controller/RemainzV2Controller.java \
   src/main/java/com/freedom/taskall_v2/web/controller/TaskallV2Controller.java
mv src/test/java/com/freedom/taskall_v2/TaskallV2ApplicationTests.java 2>/dev/null || true
mv src/test/java/com/freedom/taskall_v2/RemainzV2ApplicationTests.java \
   src/test/java/com/freedom/taskall_v2/TaskallV2ApplicationTests.java
mv src/test/java/com/freedom/taskall_v2/web/controller/RemainzV2ControllerTest.java \
   src/test/java/com/freedom/taskall_v2/web/controller/TaskallV2ControllerTest.java

grep -rl 'RemainzV2' src | xargs sed -i 's/RemainzV2/TaskallV2/g'
```

- [ ] **Step 5: 置換漏れがないことを確認する**

```bash
cd /home/develop/taskall-v2
grep -rn 'RemainzV2' src
```

Expected: 出力なし。

---

### Task 4: Gradle設定・アプリ設定・環境変数・GitHubリンクをリネームする

**Files:**
- Modify: `settings.gradle`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-local.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `README.md`
- Modify: `documents/guideline/4000001_howToUse.md`

**Interfaces:**
- Consumes: Task 3完了後の作業ツリー(パッケージリネーム済み)
- Produces: プロジェクト名・アプリ名・環境変数名・GitHubリンクが`taskall-v2`系に統一された設定一式

- [ ] **Step 1: settings.gradleを修正する**

```bash
cd /home/develop/taskall-v2
sed -i "s/rootProject.name = 'taskall-v2'/rootProject.name = 'taskall-v2'/" settings.gradle
cat settings.gradle
```

Expected: `rootProject.name = 'taskall-v2'`

- [ ] **Step 2: application.yamlのアプリ名を修正する**

```bash
cd /home/develop/taskall-v2
sed -i 's/name: taskall-v2/name: taskall-v2/' src/main/resources/application.yaml
grep -n 'name:' src/main/resources/application.yaml
```

Expected: `name: taskall-v2`

- [ ] **Step 3: 環境変数名を修正する(REMAINZ_DATASOURCE_URL → TASKALL_DATASOURCE_URL)**

```bash
cd /home/develop/taskall-v2
grep -rl 'REMAINZ_DATASOURCE_URL' src | xargs sed -i 's/REMAINZ_DATASOURCE_URL/TASKALL_DATASOURCE_URL/g'
grep -rn 'REMAINZ_DATASOURCE_URL\|TASKALL_DATASOURCE_URL' src/main/resources/application-prod.yaml
```

Expected: `TASKALL_DATASOURCE_URL`のみ表示され、`REMAINZ_DATASOURCE_URL`は表示されない。

- [ ] **Step 4: URLマッピング・GitHubリンクの一括置換(taskall-v2 → taskall-v2)**

`.java`, `.yaml`, `.md`, `.gradle`ファイルすべてを対象に、残っている`taskall-v2`表記を
`taskall-v2`に置換する(パッケージ名`remainz_v2`はTask 3で対応済みのため、ここでは
ハイフン区切りの`taskall-v2`のみが対象)。

```bash
cd /home/develop/taskall-v2
grep -rl 'taskall-v2' --include='*.java' --include='*.yaml' --include='*.md' --include='*.gradle' . \
  | xargs sed -i 's/taskall-v2/taskall-v2/g'
```

- [ ] **Step 5: 置換漏れがないことを確認する**

```bash
cd /home/develop/taskall-v2
grep -rn 'taskall-v2' --include='*.java' --include='*.yaml' --include='*.md' --include='*.gradle' .
```

Expected: 出力なし。

- [ ] **Step 6: README.mdのタイトルを修正する**

```bash
cd /home/develop/taskall-v2
sed -i '1s/.*/# taskall-v2/' README.md
head -1 README.md
```

Expected: `# taskall-v2`

---

### Task 5: copilot-instructions.mdの移植元関連セクションを書き換える

**Files:**
- Modify: `.github/copilot-instructions.md`

**Interfaces:**
- Consumes: Task 4完了後の`.github/copilot-instructions.md`(タイトル・パッケージ名は
  既に`taskall-v2`/`taskall_v2`に機械置換済み)
- Produces: 「移植元プロジェクトについて」節と「画面移植(JSP→Thymeleaf)issue対応の一般手順」節が
  taskall-v2の実情(移植元ソース非公開、issueベースの新規実装)に沿った内容に更新された
  `.github/copilot-instructions.md`

taskall-v2の`.github/copilot-instructions.md`には、ローカルクローン`/home/develop/remainz`を
参照する移植手順が書かれているが、taskall-v2の移植元「助か～る」にはソースコードが存在しない
(issueに機能概要を記述して新規実装する方針)。そのためこの2節は機械置換ではなく、内容そのものを
書き換える。

- [ ] **Step 1: 「移植元プロジェクトについて」節を置き換える**

以下のPython script を実行し、`## 移植元プロジェクトについて`から次の`## `見出しの直前までの
ブロックを新しい内容に置き換える。

```bash
cd /home/develop/taskall-v2
python3 <<'EOF'
import re

path = ".github/copilot-instructions.md"
with open(path, encoding="utf-8") as f:
    content = f.read()

old_block = """## 移植元プロジェクトについて

`taskall-v2` は、サーブレット＋JSPで実装された旧プロジェクト「taskall-v2」を、
Spring Boot＋Thymeleafへ乗せ換え、ECSコンテナなどにデプロイしやすくすることを
目的とした移植プロジェクトです。移植元は既に動作確認済みの実物資材であり、今後
頻繁に移植依頼が発生します。

- 移植元の参照先は、インターネット上のGitHub URL（`github.com/freedomRemains/taskall-v2`）
  ではなく、**ローカルクローン `/home/develop/remainz`（`develop`ブランチ）を参照する**
  こと。ローカルの方がファイル横断のgrep検索や全文参照がしやすく、正確・高速なため。
- 移植元の開発は停止しているため、ローカル資材とGitHub上の資材は実質的に同一だが、
  念のため作業前に最新化（`git pull`等）しておくと安心。
- 移植依頼時は対象のクラス名・パッケージ・機能単位を具体的に指定すると、探索の手間が
  減り精度が上がる。

### 画面移植(JSP→Thymeleaf)issue対応の一般手順

過去の画面移植issue(#9, #11)を踏まえた、今後の画面移植issue向けの一般的な手順は次の通りです。

1. 移植元JSPの`10xxx`(権限確認ラッパー)/`common/20xxx`(画面パーツ本体)のペアを、対象画面について
   洗い出す(`/home/develop/remainz/src/main/webapp/WEB-INF/jsp/`配下)。
2. 対応するThymeleafフラグメントを`src/main/resources/templates/parts/`(ラッパー)・
   `src/main/resources/templates/parts/common/`(本体)配下に、同じファイル番号(`10xxx`/`20xxx`)で
   作成する。権限確認は`T(com.freedom.taskall_v2.web.util.AuthUtil).hasReadAuth(...)`/
   `hasEditAuth(...)`をラッパーの`th:if`で呼び出す。パート横断で画面表示項目を参照する必要が
   ある場合は`T(com.freedom.taskall_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, itemKey)`
   を使う。
3. 必要なDBデータ(`SCR_ELM.SERVICE_NAME`等)を`com.remainz.*`から`com.freedom.taskall_v2.*`へ"""

assert old_block in content, "old_block not found — check for prior manual edits"

new_block = """## 移植元「助か～る」について

`taskall-v2` は、既存システム「助か～る」
（https://www.taskall.co.jp/ankeninfo/index.jsp）を、taskall-v2で確立した
ローコードエンジンを使って再構築するプロジェクトです。remainzの移植と異なり、
「助か～る」の**既存ソースコードは参照できません**。そのため以下の方針で進めます。

- 移植ではなく、実機（上記URL）の画面・挙動を確認しながら、issueに対象画面の機能概要を
  記述して**新規実装**する。元ソースの完全移植は目指さない。
- 画面外観は現代的な一般アプリの見た目に刷新し、レスポンシブ対応を行う。
- 機能は既存機能の一部を引き継ぎつつ、大幅な改修・増強を前提とする。
- issue作成時は、対象画面のURL・実機で確認した機能概要・引き継ぎたい挙動・
  変更/廃止したい挙動を具体的に記述すると、実装の精度が上がる。

### 新規画面issue対応の一般手順

「助か～る」の画面を新規実装する場合の一般的な手順は次の通りです。

1. 実機（https://www.taskall.co.jp/ankeninfo/index.jsp ）で対象画面の挙動・入力項目・
   遷移先を確認し、issueに機能概要としてまとめる。
2. `documents/guideline/4000001_howToUse.md`の「新規ページを追加する場合」の手順に従い、
   `HTML_PARTS`単位の画面部品を設計し、`src/main/resources/templates/parts/`配下に
   Thymeleafテンプレートとして作成する。権限確認は
   `T(com.freedom.taskall_v2.web.util.AuthUtil).hasReadAuth(...)`/`hasEditAuth(...)`を
   ラッパーの`th:if`で呼び出す。パート横断で画面表示項目を参照する必要がある場合は
   `T(com.freedom.taskall_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, itemKey)`
   を使う。
3. 必要なDBデータ(`URI_PATTERN`, `HTML_PAGE`, `HTML_PARTS`, `PARTS_IN_PAGE`, `SCR`,"""

content = content.replace(old_block, new_block)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("replaced")
EOF
```

- [ ] **Step 2: 置換後の内容を確認する**

```bash
cd /home/develop/taskall-v2
grep -n '## 移植元' .github/copilot-instructions.md
grep -n '/home/develop/remainz' .github/copilot-instructions.md
```

Expected: `## 移植元「助か～る」について`が1件表示され、`/home/develop/remainz`は
出力なし(0件)。

- [ ] **Step 3: 続く箇条書き部分(SCR_ELM以降)を確認し、文脈が繋がっているか目視確認する**

```bash
sed -n '30,60p' .github/copilot-instructions.md
```

`3. 必要なDBデータ(...)を...`以降、`SCR_ELM`, `REQUIRE_APROLE`, `HTML_PARTS_IN_APROLE`への
言及が自然に続いていることを確認する。もし文が不自然に途切れている場合は、以下の1行を
`3.`の末尾に追記する。

```bash
python3 - <<'EOF'
path = ".github/copilot-instructions.md"
with open(path, encoding="utf-8") as f:
    content = f.read()

marker = "3. 必要なDBデータ(`URI_PATTERN`, `HTML_PAGE`, `HTML_PARTS`, `PARTS_IN_PAGE`, `SCR`,"
if marker in content and "SCR_ELM`)を新規に作成・登録する。" not in content:
    content = content.replace(
        marker,
        marker + "\n   `SCR_ELM`, `REQUIRE_APROLE`, `HTML_PARTS_IN_APROLE`)を新規に作成・登録する。",
    )
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("appended continuation line")
else:
    print("no change needed")
EOF
```

---

### Task 6: 設計ドキュメントの棚卸しと確認

**Files:**
- Review only(原則変更なし): `documents/design/2000001_base_design.md`,
  `documents/design/2000002_model_design.md`, `documents/design/2000003_implementation_design.md`,
  `documents/design/2000004_model_driven_web_design.md`, `documents/design/2000005_top_login_mypage_thymeleaf_migration.md`

**Interfaces:**
- Consumes: Task 4完了後の作業ツリー(タイトル等は機械置換済み)
- Produces: 設計ドキュメントの内容が「taskall-v2エンジン自身の設計根拠」として妥当であることの
  確認結果(このタスクはレビューのみで、原則ファイル変更は行わない)

`documents/design/`配下の設計書には`com.remainz`(旧remainz v1)への言及が複数残るが、これらは
「なぜ現在のエンジン構造になっているか」という**taskall-v2自身の設計根拠・沿革**を説明する記述
であり、taskall-v2でも同じエンジンを使う以上、歴史的記述として引き続き正しい。そのため
機械的な削除・置換は行わない。

- [ ] **Step 1: com.remainz言及箇所を洗い出す**

```bash
cd /home/develop/taskall-v2
grep -rn 'com\.remainz\b' documents/design
```

- [ ] **Step 2: 各行が「エンジンの設計根拠の説明」であり、taskall-v2にとって不整合が
  ないことを目視確認する**

出力された各行について、「remainz(v1)からtaskall-v2への移植時にこうした」という
過去の経緯説明になっていること(＝taskall-v2への指示ではないこと)を確認する。
もし「今後もremainzを参照して移植すること」という**現在進行形の指示**になっている
記述が見つかった場合のみ、その一文を「taskall-v2で確立済みの設計のため、taskall-v2では
そのまま踏襲する」という趣旨に修正する。

- [ ] **Step 3: 確認結果をコミットメッセージ用にメモしておく(変更が無ければスキップ)**

変更が発生した場合のみ、次のTask 8のコミットに含める。変更が無ければ何もしない。

---

### Task 7: guidelineドキュメント・README関連リンクの最終確認

**Files:**
- Review/Modify: `documents/guideline/4000001_howToUse.md`
- Review/Modify: `README.md`

**Interfaces:**
- Consumes: Task 4で機械置換済みの各ドキュメント
- Produces: `taskall-v2`のURL・GitHubリンクが正しく反映されたガイドライン・README

- [ ] **Step 1: guideline内のURLがtaskall-v2に置換済みか確認する**

```bash
cd /home/develop/taskall-v2
grep -n 'remainz\|taskall-v2' documents/guideline/4000001_howToUse.md
```

Expected: すべて`taskall-v2`表記になっており、`remainz`は出力されない。

- [ ] **Step 2: README.mdの関連リンク章を確認する**

```bash
cd /home/develop/taskall-v2
grep -n 'remainz\|taskall' README.md
```

Expected: すべて`taskall`表記になっており、`remainz`は出力されない。

---

### Task 8: 置換漏れ確認・ビルド・テスト・起動検証

**Files:** なし(検証のみ)

**Interfaces:**
- Consumes: Task 1〜7完了後の`/home/develop/taskall-v2`
- Produces: ビルド・テスト・起動確認済みの状態

- [ ] **Step 1: リポジトリ全体でremainz関連の置換漏れがないか最終確認する**

```bash
cd /home/develop/taskall-v2
grep -rni 'remainz' --include='*.java' --include='*.yaml' --include='*.gradle' \
  --include='*.md' . | grep -v 'documents/design/' | grep -v 'documents/superpowers/plans/2026-07-21'
```

Expected: 出力なし(`documents/design/`配下の歴史的記述、および過去のsuperpowersプラン内の
taskall-v2固有の移行履歴は対象外として許容する)。

- [ ] **Step 2: ビルド・全テストを実行する**

```bash
cd /home/develop/taskall-v2
./gradlew build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: アプリを起動し、トップページの応答を確認する**

```bash
cd /home/develop/taskall-v2
./gradlew bootRun > /tmp/taskall-v2-bootrun.log 2>&1 &
sleep 20
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/taskall-v2/service/top.html
```

Expected: `200`

- [ ] **Step 4: アプリを停止する**

```bash
pkill -f 'taskall-v2' || true
```

(直前に起動した`bootRun`プロセスを停止する。他のプロセスに影響しないよう、`pkill`ではなく
`bootRun`実行時のプロセスIDを`$!`で保持して`kill`する方法でもよい。)

---

### Task 9: 新規リポジトリへの初回コミット・push

**Files:**
- Create: `/home/develop/taskall-v2/.git`

**Interfaces:**
- Consumes: Task 1で作成したGitHub空リポジトリ、Task 8で検証済みの作業ツリー
- Produces: `https://github.com/freedomRemains/taskall-v2`の`develop`ブランチへの初回push

- [ ] **Step 1: git初期化し、developブランチを作成する**

```bash
cd /home/develop/taskall-v2
git init
git checkout -b develop
git add .
git status
```

`build/`, `.gradle/`等のビルド生成物がステージされていないことを、taskall-v2の
`.gitignore`がそのままコピーされていることで確認する(`.gitignore`はTask 2でコピー済み)。

```bash
cat .gitignore
```

- [ ] **Step 2: 初回コミットを作成する**

```bash
cd /home/develop/taskall-v2
git commit -m "$(cat <<'EOF'
chore: taskall-v2からtaskall-v2プロジェクトを立ち上げ

taskall-v2の作業ツリーをコピーし、パッケージ名・アプリ名・環境変数名等を
taskall-v2向けにリネーム。移植元プロジェクトについてのドキュメントは
「助か～る」向けに書き換え、documents/prompts・documents/rules配下の
taskall-v2固有の開発履歴は含めていない。

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
EOF
)"
```

- [ ] **Step 3: リモートを設定しpushする**

```bash
cd /home/develop/taskall-v2
git remote add origin https://github.com/freedomRemains/taskall-v2.git
git push -u origin develop
```

- [ ] **Step 4: mainブランチも作成してpushする(taskall-v2と同じブランチ運用のため)**

```bash
cd /home/develop/taskall-v2
git checkout -b main
git push -u origin main
git checkout develop
```

- [ ] **Step 5: GitHub上でActionsが未設定であることを確認する**

`https://github.com/freedomRemains/taskall-v2/actions` にアクセスし、ワークフローが
1件も存在しないこと(「Get started with GitHub Actions」の案内画面になっていること)を
確認する。これによりCI/CDは意図的に未設定の状態でスタートしたことを確認できる
(CI/CD構築は本プランのスコープ外、別途相談する)。

---

## スコープ外事項(本プランでは対応しない)

- IaC(terraform)によるAWS環境構築
- GitHub ActionsによるCI/CD自動デプロイ
- 「助か～る」の個別機能移植issueの作成・実装
- DB初期データ(初期アカウント・ロール等)をtaskall向けに調整する作業(必要であれば別issueで対応)
