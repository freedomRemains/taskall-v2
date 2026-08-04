# 費用最小構成のため、NAT Gateway・複数AZ・Private Subnetは持たず、
# EC2を配置するPublic Subnet 1つのみのシンプルなVPCを構築する。

# [許容リスク: CKV2_AWS_11] 監視(VPC Flow Logs含む)は初期構築のスコープ外とし、documents/design/2000007_aws_build_up.mdの通り別issueで将来検討する
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name    = "${var.project_name}-vpc"
    Project = var.project_name
  }
}

# EC2からインターネット(SSM/パッケージ取得等)へ、及びインターネットからEC2(CloudFront経由)への通信のために使用する
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${var.project_name}-igw"
    Project = var.project_name
  }
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.public_subnet_cidr
  availability_zone = var.availability_zone

  # EC2にはElastic IPを個別に付与するため、Subnetでの自動パブリックIP割当は行わない
  # (checkov: CKV_AWS_130)
  map_public_ip_on_launch = false

  tags = {
    Name    = "${var.project_name}-public-subnet"
    Project = var.project_name
  }
}

# 未使用のデフォルトSecurity Groupが誤って使われないよう、全通信を拒否する状態に固定する
# (checkov: CKV2_AWS_12)
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id

  # ingress/egressルールを一切定義しないことで、全通信を拒否する
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name    = "${var.project_name}-public-rt"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}
