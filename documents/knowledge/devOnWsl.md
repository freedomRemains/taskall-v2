---
# WSL上での開発

[READMEに戻る](../../README.md)

WSLは標準でGitコマンドが使えるため、GitHubから資材を取得し、Linux(Ubuntu)上で直接開発することができる。

- WSL環境を起動し、Gitコマンドが使えることを確認する

```PowerShell
[PowerShell]
wsl -d UbuntuRestore
```

```bash
[Ubutu上のbash]
# Gitコマンドが使えることを確認する
git --version
```

- GitHubとのやり取りのためにSSH鍵を作成する

```bash
[Ubutu上のbash]
# ssh鍵を生成する(「ed25519」は鍵方式の指定、こういう固定値がある)
ssh-keygen -t ed25519 -C "[メールアカウント]@[メールホスト]"
＜例＞
ssh-keygen -t ed25519 -C "your_email@example.com"

# コマンド実行時に保存先を聞かれたら推奨されている通りに入力する(「/home/[ユーザ名]/.ssh/id_ed25519」)

# 生成された鍵のうち公開鍵をクリップボードにコピーする
cat ~/.ssh/id_ed25519.pub | clip.exe
```

- GitHubにアクセスし、次の操作で生成した鍵を登録する
  - 右上の自アカウントのアイコンをクリック
  - [Settings]-[SSH and GPG keys]を選択
  - [New SSH key]ボタンをクリック
  - [title]に任意の名前を付け、クリップボードにコピーされている公開鍵の内容を貼り付けて登録する

- GitHubから資材を取得する

```bash
[Ubutu上のbash]
git clone [GitHubリポジトリのURL]
```

- VSCodeでWSL環境上にクローンされた開発プロジェクトを開く
  - 最初にWindows上でVSCodeを起動し「WSL」という拡張機能をインストールする
    - インストールしたら、Windows上のVSCodeは閉じる
  - WSL上で次のコマンドを実行する。

```bash
[Ubutu上のbash]
# codeコマンドによりWSL(Ubuntu)上でVSCodeを起動する
code .

# 実際にはWindows上でVSCodeが起動したように見える
# 以上までで、WSL(Ubuntu)上で直接開発が可能となる。
# VSCode上でプログラムを起動した場合、それはWSL(Ubuntu)上で起動していることを示す
# なおWSL(Ubuntu)上のファイルは、Windowsエクスプローラからも参照できる
# (毎度GitHubのフィーチャーブランチに上げなくても、修正ソースは直接開いたりコピーできる)
```
