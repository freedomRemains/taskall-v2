output "role_arn" {
  description = "GitHub Actionsがaws-actions/configure-aws-credentialsで指定するIAM Role ARN"
  value       = aws_iam_role.github_actions.arn
}
