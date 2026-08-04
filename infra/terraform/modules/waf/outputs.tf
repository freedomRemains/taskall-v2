output "web_acl_arn" {
  description = "CloudFrontにアタッチするWAFv2 WebACLのARN"
  value       = aws_wafv2_web_acl.cloudfront.arn
}
