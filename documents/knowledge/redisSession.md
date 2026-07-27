---
# redisによるセッション情報の共有

[READMEに戻る](../../README.md)

- 「build.gradle」に次の設定を追加する。(SpringSecurityはコンフィグクラスの実装で使う)

```
[build.gradle]

	// SpringSessionを使用するための設定
	implementation 'org.springframework.session:spring-session-core'

	// SpringSecurityを使用するための設定
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
	testImplementation 'org.springframework.security:spring-security-test'

	// redisを使用するための設定
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	implementation 'org.springframework.session:spring-session-data-redis'
```

- SpringSessionのコンフィグクラスを追加する。

```java
package com.sb.sblib.config;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SessionConfig implements BeanClassLoaderAware {

	private ClassLoader loader;

	@Bean
	RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		return new GenericJackson2JsonRedisSerializer(objectMapper());
	}

	private ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModules(SecurityJackson2Modules.getModules(this.loader));
		return mapper;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.loader = classLoader;
	}
}
```

- 動作確認のためにはredisの起動が必要。  

環境としてはdockerを推奨。「docker-compose.yml」を次の通り記述する。

```docker
[docker-compose.yml]

# サービスを定義する
services:

  # redisの設定
  redis:

    # イメージの指定
    image: redis:latest

    # ポートの設定
    ports:

      # ホスト側の 6379 ポートを、コンテナ側の 6379 ポートにマッピングする
      - "6379:6379"
```

- 「application.properties」に、次の設定を追加する。

```
[application.properties]

## Redis session settings
spring.session.redis.repository-type=indexed
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=
```

- 普通のHttpSessionのget/setでredis経由の共有となる。  
複数のWebアプリを起動して、認証情報をセッション情報で共有する場合などに使用できる。  
本来セッション情報は単独のWebアプリ上にあるオンメモリのデータであり、他のWebアプリ  
からは参照できないが、この方法でredis経由によるセッション情報の共有が可能となる。  

```java
	@GetMapping("/top")
	public String getTop(HttpSession session) {

		// redisセッションに情報を書き込む
		session.setAttribute("redisSessionKey", "This is redis session test.");

		return "top";
	}
```

- redisに入っている値は、docker上のredisサーバにログインして確認できる。  
アプリ側での動作確認が望ましいが、動作が怪しいときはこの方法でも確認した方が確実。

```
[docker composeを起動したプロンプトで、次の順にコマンドを実行]

docker compose exec redis bash
redis-cli
keys *
hgetall [直前の keys * で取得したキー]
＜例＞
hgetall "spring:session:sessions:01cd632c-9a75-4b1d-860f-7b8960cf45f5"
```

- 上記でhgetallに成功すると、次のような画面表示を確認できる。  
(上記コードのsetAttributeで設定した内容がredis上にあることが確認できる)

```
(中略)
 3) "sessionAttr:redisSessionKey"
 4) "\"This is redis session test.\""
 (以下、略)
```
