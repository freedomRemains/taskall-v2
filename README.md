# taskall-v2

---

## 概要

本プロジェクトではAIを使ったWebアプリの実装を行います。  
なお、当該プロジェクトは「[remainz](https://github.com/freedomRemains/remainz)」及び「[remainz-v2](https://github.com/freedomRemains/remainz-v2)」という2プロジェクトを元にしています。

- AIに対して指示を出し、コーディング及びテストも原則としてAIに全て実装してもらいます。
- 作りたいWebアプリの構成を、論理設計としてマークダウン資料としてまとめます。
- 論理設計は「おおまかこうしたい」というレイヤーから徐々に、ブレイクダウンしていきます。
- 「AIに作ってもらう部品」の論理設計までブレイクダウンできたら、AIに部品作成を依頼します。
- AIに依頼して、生成された部品のコードに対し、単体テストコードを生成してもらいます。
- 単体テストを動かし、動作確認を行い、問題なければ確定された資材として保存していきます。
- こうした作業を積み重ね、最終的に論理設計した全ての部品を組み合わせてWebアプリとします。

---

## ドキュメントの管理方法

- プロジェクト直下に、次のようなフォルダ構成を作成します。

```
documents　ドキュメント配置先フォルダ
　├　design　設計書の配置フォルダ
　├　knowledge　ナレッジ資料の配置フォルダ
　├　procedure　手順書の配置フォルダ
　├　prompts　AIに対するプロンプトの配置フォルダ
　└　rules　AIが回答した規約類の配置フォルダ
```

- documents/prompts配下には、AIに対して問い合わせた内容(プロンプト)をマークダウン形式で保存します。
- documents/rules配下には、AIが回答した規約類をマークダウン形式で保存します。
- AIに作ってもらう部品も原則として全てプロンプトとその回答を保存します。
    - 当該プロジェクトのコードは全てAIが生成したものです。
    - プロンプトはissueの形で履歴になっています。
    - 元となっているコードの経緯を確認する場合は、[remainz-v2のGitHubリポジトリ](https://github.com/freedomRemains/remainz-v2)をご参照ください。
- documents/prompts配下のプロンプト(及びissueにある過去のプロンプト)の再実施という形で、再現性のある構成とします。
- 陳腐化を防ぐため、documents/rules配下のAI回答内容を定期的に見直すプロンプトを別途実施します。
- documents/rules配下の回答の最新化をAIに依頼し、それに応じてコードやテストも修正します。
- 無料で利用できるAIだとコードやテストへの反映は手作業になりがちです。
- 適宜ソースコードを自動で修正してくれるcodex系のAI使用を検討するものとします。

---

### インフラ資材について

- プロジェクト直下に、次のようなフォルダ構成を作成します。

```
infra　インフラ資材配置先フォルダ
　├　docker　docker資材の配置フォルダ(利用はローカル限定)
　└　terraform　terraform資材の配置フォルダ(未作成、AWS環境作成時に追加予定)
```

- docker配下には「docker-compose.yml」を格納する。
    - ローカルでの動作検証に利用するツールの設定を記述する想定。(利用はローカル限定)
- terraformは現在未作成だが、AWS環境作成時にはフォルダを追加して資材を格納する予定。

```
【docker起動コマンド】
cd [path to taskall-v2]/infra/docker
docker compose up -d

＜例＞
cd /home/develop/taskall-v2/infra/docker
docker compose up -d
```

---

【関連リンク】

- [基本設計](documents/design/2000001_base_design.md)
- [モデル設計](documents/design/2000002_model_design.md)
- [実装設計](documents/design/2000003_implementation_design.md)
- [開発環境構築手順(WSL及びdocker)](documents/procedure/3000001_wsl.md)
- [開発環境構築手順(VSCode)](documents/procedure/3000011_vscode.md)
- [Terraform環境構築手順](documents/procedure/3000021_terraform.md)

---

【ナレッジ】

- [プロパティファイルの記述内容について](documents/knowledge/propContents.md)
- [プロパティファイルの配置について](documents/knowledge/propPos.md)
- [【ToDo】環境ごとのプロパティファイルについて](documents/knowledge/propEnv.md)
- [Gradle ビルドでテストエラーが出る場合の対応について](documents/knowledge/gradleError.md)
- [h2 データベースを使用するための設定について](documents/knowledge/h2.md)
- [SpringDoc の使用方法](documents/knowledge/springDoc.md)
- [マークダウンでシーケンス図を記述する方法](documents/knowledge/markdownSequence.md)
- [MyBatis でスネーク記法の DB カラム名をキャメル記法のエンティティに対応させる方法](documents/knowledge/myBatisSnakeToCamel.md)
- [【ToDo】メッセージプロパティの設定方法](documents/knowledge/msgProp.md)
- [【ToDo】メール送信方法](documents/knowledge/sendMail.md)
- [redis によるセッション情報の共有](documents/knowledge/redisSession.md)
- [WSL 環境の構築](documents/knowledge/wslEnv.md)
- [WSL 上での開発](documents/knowledge/devOnWsl.md)
- [ビルド手順](documents/knowledge/build.md)
- [JUnit](documents/knowledge/junit.md)
- [DBUnit](documents/knowledge/dbunit.md)
- [API の返却 JSON に、thymeleaf レンダリングした HTML を載せる方法](documents/knowledge/thymeleafJson.md)
- [MinIO の使い方](documents/knowledge/minio.md)
- [バッチについて](documents/knowledge/batch.md)
- [ログについて](documents/knowledge/log.md)
