variable "project_name" {}
variable "key_name" {}
variable "vpc_id" {}
variable "public_subnet_id" {}
variable "instance_type" { default = "t3.micro" }
variable "db_host" {}
variable "db_password" { sensitive = true }
variable "aws_access_key_id" { sensitive = true }
variable "aws_secret_access_key" { sensitive = true }
variable "cognito_user_pool_id" {}
variable "cognito_client_id" {}
variable "s3_bucket_name" {}
variable "sqs_queue_url" {}
variable "ses_from_email" {}
variable "jwt_secret" { sensitive = true }
variable "cors_allowed_origins" {}

# Doc 7: SSH ingress was 0.0.0.0/0 (open to the entire internet) - no default
# here on purpose. Set this to your own IP (e.g. "203.0.113.4/32") or a VPN/
# office CIDR before applying; there's no safe default I can guess on your
# behalf, and an open one is worse than forcing an explicit value.
variable "ssh_allowed_cidr" {
  description = "CIDR allowed to SSH into the backend EC2 instance - never 0.0.0.0/0"
}