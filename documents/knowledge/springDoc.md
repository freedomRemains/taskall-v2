---
# SpringDocの使用方法

[READMEに戻る](../../README.md)

「build.gradle」にSpringDocを使用するための設定記述が必要。

```
[build.gradle]

	// SpringDocを使用するための設定
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'
```

「application.properties」に、次の設定が必要。

```
[application.properties]

## SpringDoc
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

- ブラウザを開き、次のURLにアクセスすると、自動生成されたAPI仕様書が確認できる。  
http://localhost:8080/swagger-ui.html
- 次のURLにアクセスすると、swaggerのyamlをダウンロードできる。  
http://localhost:8080/api-docs.yaml
