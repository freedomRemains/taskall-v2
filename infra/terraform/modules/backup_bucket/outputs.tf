output "bucket_name" {
  description = "DBバックアップ用S3バケット名"
  value       = aws_s3_bucket.backup.bucket
}

output "bucket_arn" {
  description = "DBバックアップ用S3バケットARN"
  value       = aws_s3_bucket.backup.arn
}
