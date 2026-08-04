output "state_bucket_name" {
  description = "Terraform stateを保管するS3バケット名(prod環境等のbackend.confに設定する)"
  value       = aws_s3_bucket.terraform_state.bucket
}

output "lock_table_name" {
  description = "Terraform Lock用DynamoDBテーブル名(prod環境等のbackend.confに設定する)"
  value       = aws_dynamodb_table.terraform_lock.name
}
