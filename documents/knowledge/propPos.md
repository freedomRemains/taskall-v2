---
# プロパティファイルの配置について

[READMEに戻る](../../README.md)

- 「application.properties」は、親となるWebアプリ側にのみ配置する。  
  (　「custom.properties」のような、従属するプロパティファイルも同様)
- ただし従属するプロジェクト側を単独のGradleプロジェクトとし、ビルドに  
  よって全テストを行う場合、「application.properties」及び従属する  
  「custom.properties」などのプロパティファイルは、test/resources配下  
  に配置する。(こうすると、JUnit全テスト時にはtest/resources配下に配置  
  したプロパティファイルに基づいて動作させることができる)
