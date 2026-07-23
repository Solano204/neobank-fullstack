variable "project_name" {}
variable "vpc_id" {}
variable "private_subnet_ids" { type = list(string) }
variable "ledger_queue_arn" {}
variable "fraud_queue_arn" {}
variable "analytics_queue_arn" {}
variable "notifications_queue_arn" {}
variable "dynamodb_table" {}
variable "s3_bucket_name" {}
variable "sns_fraud_arn" {}
variable "sns_kyc_arn" {}
variable "sns_txn_arn" {}
variable "db_url" {}
variable "db_password" { sensitive = true }
variable "aws_access_key_id" { sensitive = true }
variable "aws_secret_access_key" { sensitive = true }
variable "aws_region" {}
