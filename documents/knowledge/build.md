---
# ビルド手順

[READMEに戻る](../../README.md)

本プロジェクトではGradleによるビルドを実施する。

- Windowsでは次のコマンドを実行する。

```build.gradle
cd [build.gradleがあるパス]
chcp 65001
set JAVA_HOME=[Javaインストールディレクトリ]
set JAVA_OPTS=-Dfile.encoding=UTF-8
gradlew clean
gradlew build

＜例＞
cd C:\10_local\60_GitHub\jl
chcp 65001
set JAVA_HOME=C:\pleiades\java\17
set JAVA_OPTS=-Dfile.encoding=UTF-8
gradlew clean
gradlew build
```
