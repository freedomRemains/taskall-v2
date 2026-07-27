---
# MinIOの使い方

[READMEに戻る](../../README.md)

MinIOはAWS S3と同じ仕組みをもつdockerコンテナで、ローカルPC上でAWS S3アクセスを模擬できる。
MinIOの使い方は、次の通り。

- 「docker-compose.yml」に、MinIOの起動設定を追加する。

```docker
  # MinIOの設定
  minio:

    # イメージの指定
    image: minio/minio:latest

    # コンテナ名の設定
    container_name: minio

    # ポートの設定
    ports:

      # API用には9000ポート、管理コンソール用には9001ポートを使用
      - "9000:9000"
      - "9001:9001"

    # 環境変数の設定
    environment:

      # MinIOのルートユーザーとパスワードを設定
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123

    # ボリュームの設定
    volumes:

      # MinIOのデータを永続化するためのボリュームを指定
      - minio_data:/data

    # コマンドの設定
    # MinIOサーバーを起動し、9001ポートで管理コンソールを提供
    command: server /data --console-address ":9001"

  # MinIOのクライアントを使用してバケットを作成するためのサービス
  createbuckets:

    # イメージの指定
    image: minio/mc

    # 当該サービスがMinIOが起動してから実行されるよう、依存関係を設定
    depends_on:
      - minio

    # 起動時にデフォルトのバケットを生成するためのエントリポイント設定
    entrypoint: >
      /bin/sh -c "
        until mc alias set myminio http://minio:9000 minioadmin minioadmin123; do
          echo 'Waiting for MinIO...';
          sleep 2;
        done;
        mc mb --ignore-existing myminio/appstrage;
        exit 0;
      "

# ボリュームの定義
volumes:

  # MinIOのデータを永続化するためのボリューム
  minio_data:
```

- SpringBootでMinIOにアクセスするためには、「build.gradle」に次の設定を追加する。

```Gradle
	// AWS S3(AWS SDK)を使用するための設定
	implementation 'software.amazon.awssdk:s3:2.25.33'
	implementation 'software.amazon.awssdk:auth:2.25.33'
```

- AWS S3に接続するためのコンフィグクラスが必要となる。

```Java
package com.sb.sblib.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sb.sblib.util.PropUtil;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final PropUtil propUtil;

    @Bean
    public S3Client s3Client() {

        // S3アクセスのためのクレデンシャル情報を生成する
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                propUtil.getS3().get("accessKey"),
                propUtil.getS3().get("secretKey")
        );

        // S3クライアントを生成する
        return S3Client.builder()
                .endpointOverride(URI.create(propUtil.getS3().get("endpoint")))
                .region(Region.of(propUtil.getS3().get("region")))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(config -> config.pathStyleAccessEnabled(true))
                .build();
    }
}
```

- 次のAWS S3ユーティリティを経由して、アップロード／ダウンロードといった操作を行う。

```Java
package com.sb.sblib.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
@RequiredArgsConstructor
public class AwsS3Util {

    /** プロパティユーティリティ */
    private final PropUtil prop;

    /** AWS S3クライアント */
    private final S3Client s3Client;

    /**
     * S3にファイルをアップロードする。
     * 
     * @param localDirPath 元ファイルのディレクトリパス
     * @param localFileName 元ファイル名
     * @param s3DirPath アップロード先のディレクトリパス
     * @param s3FileName アップロード先のファイル名
     * @throws Exception 例外
     */
    public void upload(String localDirPath, String localFileName, String s3DirPath, String s3FileName) throws Exception {
        upload(localDirPath + "/" + localFileName, s3DirPath + "/" + s3FileName);
    }

    /**
     * S3にファイルをアップロードする。
     * 
     * @param localFilePath 元ファイルのファイルパス
     * @param s3FilePath アップロード先のファイルパス
     * @throws Exception 例外
     */
    public void upload(String localFilePath, String s3FilePath) throws Exception {
        ClassPathResource classPathResource = new ClassPathResource(localFilePath);
        upload(classPathResource.getFile(), s3FilePath);
    }

    /**
     * S3にファイルをアップロードする。
     * 
     * @param localFile 元ファイル(File型)
     * @param s3FilePath アップロード先のファイルパス
     * @throws Exception 例外
     */
    public void upload(File localFile, String s3FilePath) throws Exception {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(prop.getS3().get("bucket"))
                .key(s3FilePath)
                .build();
        s3Client.putObject(request, localFile.toPath());
    }

    /**
     * S3からファイルをダウンロードする。
     * 
     * @param s3DirPath ダウンロードするファイルのディレクトリパス
     * @param s3FileName ダウンロード対象のファイル名
     * @param localDirPath ダウンロード先のディレクトリパス
     * @param localFileName ダウンロードファイル名
     * @throws Exception
     */
    public void download(String s3DirPath, String s3FileName, String localDirPath, String localFileName) throws Exception {
        download(s3DirPath + "/" + s3FileName, localDirPath + "/" + localFileName);
    }

    /**
     * S3からファイルをダウンロードする。
     * 
     * @param s3FilePath ダウンロードするファイルのファイルパス
     * @param localFilePath ダウンロード先のファイルパス
     * @throws Exception 例外
     */
    public void download(String s3FilePath, String localFilePath) throws Exception {
        download(s3FilePath, new File(localFilePath));
    }

    /**
     * S3からファイルをダウンロードする。
     * 
     * @param s3FilePath ダウンロードするファイルのファイルパス
     * @param localFile ダウンロード先のローカルファイル(File型)
     * @throws Exception 例外
     */
    public void download(String s3FilePath, File localFile) throws Exception {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(prop.getS3().get("bucket"))
                .key(s3FilePath)
                .build();
        try (ResponseInputStream<GetObjectResponse> s3Obj = s3Client.getObject(request);
                FileOutputStream fos = new FileOutputStream(localFile)) {
            s3Obj.transferTo(fos);
        }
    }

    /**
     * S3上の所定のディレクトリパスにあるファイルの一覧を取得する。
     * サブディレクトリがある場合は、再帰的にたどって全てのファイルパスを取得できる。
     * ＜例＞
     * xxx.txt 　←　指定したディレクトリ直下のファイル
     * subdir1/yyy.txt　←　サブディレクトリ配下にあるファイル
     * subdir2/zzz.txt　←　別のサブディレクトリ配下にあるファイル
     * 
     * @param targetDirPath 一覧取得対象のディレクトリパス
     * @return S3上にあるファイル名のリスト
     */
    public List<String> listFiles(String targetDirPath) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(prop.getS3().get("bucket"))
                .prefix(targetDirPath)
                .build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }

    /**
     * S3上からファイルを削除する。
     * 
     * @param targetDirPath 削除するファイルのディレクトリ
     * @param targetFileName 削除するファイル名
     */
    public void delete(String targetDirPath, String targetFileName) {
        delete(targetDirPath + "/" + targetFileName);
    }

    /**
     * S3上からファイルを削除する。
     * 
     * @param targetFilePath 削除するファイルのパス
     */
    public void delete(String targetFilePath) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(prop.getS3().get("bucket"))
                .key(targetFilePath)
                .build();
        s3Client.deleteObject(request);
    }
}
```
