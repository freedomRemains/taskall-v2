---
# WSL環境の構築

[READMEに戻る](../../README.md)

- WindowsPowerShellにて、次のコマンドを実行する。

```PowerShell
[PowerShell]
wsl --install
```

- インストールできない場合は、chatGPTやGitHub copilotといったAIに聞く。  
「WSL インストール」などで地道にGoogle検索しても出てくる。  
たいていはコントロールパネルで「Windowsの機能の有効化または無効化」でWSLを有効にすれば解決できる。  
まれにPC起動時のBIOS設定変更が必要なことがあるが、2025年1月時点での一般的なPCではまずその問題は起きない。

- 次のコマンドを実行し、WSLのバージョンが2系(もしくはそれ以上)であることを確認する。

```PowerShell
[PowerShell]
wsl -l -v
```

- WSLが2系でない場合は、次のコマンドを実行する。  
時間経過により「2」のところは「3」とか「4」になるかも知れない。  
AIに聞くかGoogle検索で最新情報を確認し、適宜コマンドは見直す。

```PowerShell
[PowerShell]
wsl --set-default-version 2
```

- 次のコマンドでWSLを最新化する。

```PowerShell
[PowerShell]
wsl --update
```

- 次のコマンドでWSL環境にUbuntuをインストールする。  
インストール時にはユーザ名とパスワードを聞かれるので準備しておくこと。  
パスワードはポリシーの制約があるので、あまり単純なものはダメ。
  - ユーザ名：develop
  - パスワード：Develop01

```PowerShell
[PowerShell]
wsl --install Ubuntu
```

- なお初回起動時既にUbuntuがインストールされている場合がある。  
ユーザ名とパスワードを入力せずにUbuntuが起動した場合はシャットダウンし、  
Ubuntuを一度削除して再度インストールすること。  
(シャットダウンのコマンドは、別のWindowsPowerShellから実行する)

```PowerShell
[PowerShell]
wsl --shutdown
wsl -l -v
wsl --unregister Ubuntu
wsl -l -v
wsl -l -o
wsl --install -d Ubuntu
```

- なおインストール可能なLinux OSの一覧は、次のコマンドで確認することができる。

```PowerShell
[PowerShell]
wsl -l -o
```

- 「wsl -l -v」コマンドで確認できるインストール済みOS一覧でUbuntuがデフォルト選択(「*」付き)  
になっていない場合、次のコマンドでデフォルト選択とする。

```PowerShell
[PowerShell]
wsl --set-default Ubuntu
wsl -l -v
```

- 別のWindowsPowerShellを起動して次のコマンドを実行し、シャットダウンと起動ができることを確認する。

```PowerShell
[PowerShell]
wsl --shutdown
wsl -d Ubuntu
```

- Ubuntu環境にdocker及びdocker composeをインストールする。  
このコマンドはdockerの本家サイトに掲載されており、AIなどに聞いても分かる。  
情報は常に陳腐化する可能性があるので、うまくいかないときは最新のコマンドを調べること。

```bash
[Ubutu上のbash]
# 2025/09/13 AIに質問して得られた手順
# 古いパッケージがある場合は削除する
for pkg in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
  sudo apt-get remove -y $pkg
done

# 必要なパッケージのインストールを行う
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# GPTキーの取得及び保存を行う
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo tee /etc/apt/keyrings/docker.asc > /dev/null
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Docker公式リポジトリを追加する
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# docker及びdocker composeをインストールする
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# ユーザをdockerグループに追加する(dockerコマンド時にsudo不要とするための設定)
sudo usermod -aG docker $USER
```

- 上記のdockerグループへのユーザ追加コマンド実行後は、必ず一度Ubuntuを落とし、再度立ち上げること。

```PowerShell
[PowerShell]
wsl --shutdown
wsl -d Ubuntu
```

- WSLでUbuntuを立ち上げなおしたら、docker及びdocker composeがインストールできていることを確認する

```bash
[Ubutu上のbash]
# docker及びdocker composeがインストールできていることを確認する
docker version
docker compose version
docker run hello-world
```

- Ubuntu起動時にdockerを起動させたい場合は、次の手順を実行すること。(「~/.bashrc」の編集)

```bash
[Ubutu上のbash]
vi ~/.bashrc

# 表示されたファイルの末尾にカーソルを合わせ、追記モード(aキー押下)にする
a

# ~/.bashrc の末尾に、次の記述を追加する
if ! pgrep -x "dockerd" > /dev/null; then
  sudo /usr/bin/dockerd > /dev/null 2>&1 &
fi

# 上書き保存でviを終了する
(Escキーを押し、追記モードを終了してから次の通り入力する)
:wq
```

- WSLを落として再立ち上げしてもdocker及びdocker composeが使えることを確認する

```PowerShell
[PowerShell]
wsl --shutdown
wsl -d Ubuntu
```

```bash
[Ubutu上のbash]
docker version
docker compose version
docker run hello-world
```

- docker及びdocker composeインストールを完了できたら、WSL(Ubuntu)環境バックアップの準備を行う

```bash
[Ubutu上のbash]
# 復元してもデフォルトユーザをdevelopに保つための設定ファイルを作成する(既存の場合は末尾に追記する)
sudo nano /etc/wsl.conf

# 次の通り記述し、Ctrl + xでnanoを抜ける(このとき上書き保存すること)
[user]
default=develop

# 記述変更を確認する
cat /etc/wsl.conf

＜画面表示例(追記内容が確認できればOK)＞
develop@WSL:~$ cat /etc/wsl.conf
[boot]
systemd=true
[user]
default=develop
```

- WSL(Ubuntu)環境のバックアップを行う

```PowerShell
[PowerShell]
wsl --shutdown
wsl -l -v

wsl --export [Linuxディストリビューション名] [保存先パス]\UbuntuBackup.tar
＜例＞
wsl --export Ubuntu C:\10_local\20_docker\70_wsl\10_backup\UbuntuBackup.tar
```

- 次の手順を実行し、WSL(Ubuntu)環境が復元できることを確認する  
(復元用ディレクトリを作成し、そこに復元)

```PowerShell
[PowerShell]
mkdir [復元用ディレクトリ]
＜例＞
mkdir C:\10_local\20_docker\70_wsl\20_restore

wsl --import [新しいディストリビューション名] [復元先フォルダ] [バックアップファイル] --version 2
＜例＞
wsl --import UbuntuRestore C:\10_local\20_docker\70_wsl\20_restore C:\10_local\20_docker\70_wsl\10_backup\UbuntuBackup.tar --version 2

wsl -l -v
wsl --set-default UbuntuRestore
wsl -l -v
wsl -d UbuntuRestore
```

- 以降は復元環境で作業する。(オリジナルも万一のためのdockerクリーンインストール状態として残す)

```PowerShell
[PowerShell]
wsl -d UbuntuRestore
```

```bash
[Ubutu上のbash]
docker version
docker compose version
docker run hello-world
```

- 次のメンテナンスコマンドを定期的に実行すること(バックアップも復元環境のものとする)  
【コマンドが復元環境の循環バックアップと復元になっているので注意すること！！】
```PowerShell
[PowerShell]
wsl --update
[上記のバックアップコマンドの復元環境版]
＜例＞
wsl --export UbuntuRestore C:\10_local\20_docker\70_wsl\10_backup\UbuntuRestoreBackup.tar
```

- 環境復元も定期的に確認することが望ましい  
【コマンドが復元環境の循環バックアップと復元になっているので注意すること！！】
```PowerShell
[PowerShell]
wsl --update
[上記のバックアップコマンドの復元環境版]
＜例＞
wsl --export UbuntuRestore C:\10_local\20_docker\70_wsl\10_backup\UbuntuRestoreBackup.tar

wsl -l -v
wsl --unregister UbuntuRestore
wsl -l -v
[上記の復元コマンドの復元環境版]
＜例＞
wsl --import UbuntuRestore C:\10_local\20_docker\70_wsl\20_restore C:\10_local\20_docker\70_wsl\10_backup\UbuntuRestoreBackup.tar --version 2

wsl -l -v
wsl --set-default UbuntuRestore
wsl -l -v
wsl -d UbuntuRestore
```

- 最近Windowsの仕様が変わり、以前ならWSLのフォルダに無条件にアクセスできていたが、今は次のパス指定が必要。

```
[エクスプローラのアドレスバー上で、次の通り入力]
\\wsl$\UbuntuRestore
※　明示的に「\\wsl$」を指定しないと、WSL配下の構成が参照できない。
```

- WSL上にUbuntuを作成すると、エクスプローラでルート以下のディレクトリ構成にアクセスできる。  
/home配下など、適切な場所にdocker-compose.ymlを配置する。

- 次のようなコマンドでdocker composeを起動する。

```PowerShell
[PowerShell]
cd [docker-compose.ymlの配置先ディレクトリ]
docker compose up -d
＜例＞
cd /home/develop/tool/docker
docker compose up -d
```
