variable "project_name" {}
variable "key_name" {}
variable "vpc_id" {}
variable "public_subnet_id" {}
variable "instance_type"        { default = "t3.micro" }
variable "db_host" {}
variable "db_password"          { sensitive = true }
variable "aws_access_key_id"    { sensitive = true }
variable "aws_secret_access_key" { sensitive = true }
variable "cognito_user_pool_id" {}
variable "cognito_client_id" {}
variable "s3_bucket_name" {}
variable "sqs_queue_url" {}
variable "ses_from_email" {}
variable "jwt_secret"           { sensitive = true }
variable "cors_allowed_origins" {}