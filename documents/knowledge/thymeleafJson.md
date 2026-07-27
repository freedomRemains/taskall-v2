---
# APIの返却JSONに、thymeleafレンダリングしたHTMLを載せる方法

[READMEに戻る](../../README.md)

- APIで部分レンダリングするHTML箇所をfragmentとした親HTMLを作成する。

```html
【sbwap/src/main/resources/templates/render.html】

<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
  <head>
    <title>バリデーション</title>
    <link rel="stylesheet" th:href="@{/css/common.css}">
    <meta name="_csrf" content="${_csrf.token}"/>
    <meta name="_csrf_header" content="${_csrf.headerName}"/>
  </head>
  <body>
    <h1>バリデーション</h1>

    <div th:replace="~{parts/validationForm :: validationForm}"></div>

    <script th:src="@{/js/common.js}"></script>
  </body>
</html>
```

- 部分レンダリングするfragmentのHTML部品を作成する。
```html
【sbwap/src/main/resources/templates/parts/renderParts.html】

    <div id="renderTarget" th:fragment="renderParts">
      <form id="renderForm" method="post" th:object="${renderItems}">
        <div id="dispArea">
          入力値：<input type="text" name="inputValue"><br />
          【サーバ到達確認】入力値：<span th:text="${renderItems.inputValue}"></span><br />
          <span id="renderError" th:text="${renderItems.renderError}"></span><br />
        </div>
        <input type="submit" name="submitRender" value="レンダリングAPI呼び出し">
      </form>
    </div>
```

- HTMLに対応するthymeleafレンダリングのコントローラを追加する。

```Java
package com.sb.sbwap.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.sb.sbwap.dto.RenderItemsDto;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RenderController {

	@GetMapping("/render")
	public String getRender(Model model) {

		// モデルに画面表示用のDTOを設定する
		RenderItemsDto renderItemsDto = new RenderItemsDto();
		model.addAttribute("renderItems", renderItemsDto);

        // 遷移先画面名を呼び出し側に返却する
        return "render";
	}
}
```

- thymeleafとコントローラの間でやり取りするDTOクラスを追加する。

```Java
package com.sb.sbwap.dto;

import lombok.Data;

@Data
public class RenderItemsDto {

	private String inputValue;
	private String renderError;
}
```

- RestControllerにAPIを追加する。

```Java
@RestController
@RequiredArgsConstructor
public class ApiController {

(中略)

    /** テンプレートエンジン */
    private final TemplateEngine templateEngine;

(中略)

    @PostMapping("/api/v1/render")
    public ResponseEntity<Map<String, Object>> postRender(@RequestBody Map<String, Object> requestBody) {

        // コンテキストを生成し、リクエストを設定する
        Context context = new Context();
        RenderItemsDto renderItems = new RenderItemsDto();
        renderItems.setInputValue((String) requestBody.get("inputValue"));
        renderItems.setRenderError((String) requestBody.get("renderError"));
        context.setVariable("renderItems", renderItems);

        // thymeleafテンプレートエンジンを使ってレンダリングを行う
        String html = templateEngine.process("parts/renderParts", context);
        Map<String, Object> response = new HashMap<>();
        response.put("html", html);

        // レスポンスを返却する
        return ResponseEntity.ok(response);
    }
}
```

- JavaScriptにAPI呼び出しコードを記述する。

```JavaScript
【sbwap/src/main/resources/static/js/render.js】

// API呼び出しボタンをクリックしたときのイベントハンドラを指定する
document.getElementById('renderForm').addEventListener('submit', async function(event) {

  // デフォルトの挙動を抑止する(フォーム送信を抑止)
  event.preventDefault();

  // フォームのデータをJSONに変換する
  const form = event.target;
  const formData = new FormData(form);
  const jsonData = {};
  formData.forEach((value, key) => {
    jsonData[key] = value;
  });

  try {
    // APIを呼び出す
    const response = await fetch('http://localhost:8080/api/v1/render', {
      method: 'POST',
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(jsonData)
    });

    if (!response.ok) {
      throw new Error(`HTTPエラー: ${response.status}`);
    }

    const result = await response.json();
    console.log('APIレスポンス：', result);

    // サーバ側でレンダリングしたHTMLを表示する
    const parser = new DOMParser();
    const newElement = parser.parseFromString(result.html, 'text/html').body.firstElementChild;
    document.getElementById('renderTarget').replaceWith(newElement);

  } catch (error) {

    console.error('送信エラー：', error);
  }
});
```

- SpringBootプログラムを起動し、[URL](http://localhost:8080/render)にアクセスする。
- 「入力値」に入力して「レンダリングAPI呼び出し」ボタンをクリックする。
- 「【サーバ到達確認】」の項に入力値が反映されていれば、コードは動いている。
