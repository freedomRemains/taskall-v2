# Terraform 環境構築手順

---

[READMEに戻る](../../README.md)

---


Terraform を使って AWS 上の EC2 インスタンスを構築するためには、以下の環境設定が必要です。

1. **Terraform のインストール**:

   - 公式サイトから Terraform をダウンロードし、インストールします。
     - [Terraform のダウンロードページ](https://www.terraform.io/downloads.html)

2. **AWS CLI のインストール**:

   - AWS CLI をインストールし、AWS アカウントの認証情報を設定します。
     - [AWS CLI のインストール手順](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
     - 認証情報の設定: `aws configure`

3. **AWS アカウントの設定**:

   - AWS アカウントを作成し、アクセスキーとシークレットキーを取得します。
   - 取得したキーを使って、AWS CLI で認証情報を設定します。
     ```sh
     aws configure
     ```

4. **Terraform 設定ファイルの作成**:
   - Terraform の設定ファイルを作成します。通常は次のように、管理しやすいようファイルを分割します。(構成例です。実際の構成は別で構いません)
     - `provider.tf`: AWS プロバイダーの設定
     - `main.tf`: EC2 インスタンスの設定
     - `variables.tf`: 変数の定義
     - `outputs.tf`: 出力設定

ここでは例示として、上記の基本的な設定ファイルの例を示します。

### provider.tf

```hcl
provider "aws" {
  region = "us-west-2"  # 使用するリージョンを指定
}
```

### variables.tf

```hcl
variable "region" {
  description = "AWS region"
  default     = "us-west-2"
}

variable "instance_type" {
  description = "EC2 instance type"
  default     = "t2.micro"
}

variable "ami" {
  description = "AMI ID"
  default     = "ami-0c55b159cbfafe1f0"  # Amazon Linux 2 AMIの例
}
```

### main.tf

```hcl
resource "aws_instance" "example" {
  ami           = var.ami
  instance_type = var.instance_type

  tags = {
    Name = "example-instance"
  }
}
```

### outputs.tf

```hcl
output "instance_id" {
  description = "The ID of the EC2 instance"
  value       = aws_instance.example.id
}

output "instance_public_ip" {
  description = "The public IP of the EC2 instance"
  value       = aws_instance.example.public_ip
}
```

5. **Terraform の実行**:

   - Terraform の初期化、プランの作成、適用を行います。

  ```sh
  # 初期化
  terraform init

  # プランの作成
  terraform plan

  # 適用
  terraform apply

  # 削除(構築した内容を削除して、元の何もない状態に戻したいとき)
  terraform destroy
  ```

なお実行のためには %[teraterm.exeのパス]%\teraterm xxx という指定が必要です。(通常、パスは通っていません)

これにより、指定した設定に基づいて AWS 上に EC2 インスタンスが作成されます。削除や再構築は、`terraform destroy`コマンドを使って EC2 インスタンスを削除し、その後再度`terraform apply`を実行することで行えます。

必要に応じて、詳細な設定や追加のリソース（セキュリティグループ、VPC など）を設定ファイルに追加してください。

【注記】SSM利用予定のため、以降の記述は参照不要です。(念のため、記載は残しています)

6. **EC2サーバとのSSH通信のためのキーペア作成について**

   - EC2サーバとのSSH通信のためにはキーペアが必要

```shell
aws ec2 create-key-pair --key-name WebApKey --query "KeyMaterial" --output text > ~/.ssh/WebApKey.pem
```

Windowsで実際に打ったコマンドは、次の通り。(必ず「.ssh」ディレクトリがある位置(ホームディレクトリ)で実行すること)

```command
aws ec2 create-key-pair --key-name WebApKey --query "KeyMaterial" --output text>.ssh/WebApKey.pem
```

SSH接続のためには、EC2起動後、次のようなコマンドを実行する。

```command
rem コマンドテンプレート
ssh -i ~/.ssh/[キーペアファイル名].pem ec2-user@[Elastic IP]
rem ＜例＞
ssh -i ~/.ssh/WebApKey.pem ec2-user@18.181.47.235
```

---

[READMEに戻る](../../README.md)

---
