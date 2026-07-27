---
# プロパティファイルの記述内容について

[READMEに戻る](../../README.md)

- 「application.properties」にはSpringBootの起動時に読み込む設定を記述する。  
  (デフォルトの挙動を変えたいときに、SpringBootが持っているプロパティの設定を  
  個別に指定するための設定ファイル)
- ユーザが独自に定義したプロパティを認識させたい場合は「custom.properties」  
  のような別のプロパティファイルとすること。
- ユーザ定義プロパティファイル(ここでは例示として「custom.properties」という  
  ファイル名とする)を取り込むためには、「application.properties」に次のような  
  設定を追加する。

```
[application.properties]

## Custom property settings
spring.config.import=custom.properties
```
