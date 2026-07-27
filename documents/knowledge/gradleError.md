---
# Gradleビルドでテストエラーが出る場合の対応について

[READMEに戻る](../../README.md)

Gradleビルド時にテストエラーとなってしまう場合の対応は、次の通り。

- テストエラーを確認する。
- インジェクションするクラスが存在しないというエラーの場合、  
  使っているSpringBootの機能に必要な設定が「application.properties」  
  に記載されているか確認する。  
  ＜例＞  
  - データベースアクセスがある場合、「application.properties」に  
    「spring.datasource～」で始まる諸設定がないと動かない。
  - JavaMailSenderを使う場合、「spring.mail.host」と「spring.mail.port」  
    の設定がないと動かない。
