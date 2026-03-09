variable "project_name" {}
variable "vpc_id" {}
variable "private_subnet_ids"   { type = list(string) }
variable "sqs_queue_arn" {}
variable "sqs_queue_url" {}
variable "dynamodb_table" {}
variable "s3_bucket_name" {}
variable "sns_fraud_arn" {}
variable "sns_kyc_arn" {}
variable "sns_txn_arn" {}
variable "db_url" {}
variable "db_password"          { sensitive = true }
variable "aws_access_key_id"    { sensitive = true }
variable "aws_secret_access_key" { sensitive = true }
variable "aws_region" {}