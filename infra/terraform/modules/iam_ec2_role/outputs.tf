output "instance_profile_name" {
  description = "EC2にアタッチするInstance Profile名"
  value       = aws_iam_instance_profile.ec2.name
}
