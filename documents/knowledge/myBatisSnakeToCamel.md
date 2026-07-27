---
# MyBatisでスネーク記法のDBカラム名をキャメル記法のエンティティに対応させる方法

[READMEに戻る](../../README.md)

- 「application.properties」に次の設定を記述する。

```
## MyBatis settings
mybatis.configuration.map-underscore-to-camel-case=true
```
