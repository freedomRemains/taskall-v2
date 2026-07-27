---
# VSCode設定

[READMEに戻る](../../README.md)

- VSCodeは開いたフォルダがSpringBootプロジェクトだった場合、「.vscode」という隠しフォルダに設定ファイルを配置できる。
- 「.vscode」配下の「settings.json」に所定の設定を書いておくと、VSCode本体の「settings.json」を設定しなくても、すぐに動かせる。
- 2026/04/29時点の推奨設定は、次の通り。

```settings.json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "java.format.settings.url": ".vscode/eclipse-java-google-style.xml",
  "java.format.settings.profile": "GoogleStyle",

  "maven.executable.preferMavenWrapper": true,
  "java.import.gradle.wrapper.enabled": true,
  "java.import.gradle.enabled": true,
  "java.import.maven.enabled": true,

  "spring-boot.ls.checkForUpdates": true,
  "spring-boot.ls.showBootHints": true,

  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.organizeImports": "explicit"
  },

  "files.watcherExclude": {
    "**/target/**": true,
    "**/.git/**": true
  },
  "search.exclude": {
    "**/target": true
  }
}
```

- 「.vscode」フォルダは「.gitignore」対象だが、「.vscode」配下の「settings.json」を共有したい場合は設定を外してもよい。
