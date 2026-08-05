output "certificate_arn" {
  description = "検証済みACM証明書のARN(CloudFrontのviewer_certificateで使用)"
  value       = aws_acm_certificate_validation.cert.certificate_arn
}
