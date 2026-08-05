output "bucket_name" {
  description = "CI/CDアーティファクト用S3バケット名"
  value       = aws_s3_bucket.artifact.bucket
}

output "bucket_arn" {
  description = "CI/CDアーティファクト用S3バケットARN"
  value       = aws_s3_bucket.artifact.arn
}
