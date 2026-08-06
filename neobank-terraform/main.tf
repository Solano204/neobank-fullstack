terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.57"
    }
  }

  # Doc 7: no backend block existed - state defaults to local
  # (terraform.tfstate on whoever's machine runs apply), which means no
  # locking (two concurrent applies can corrupt state) and the state file -
  # which holds every value in this config in plaintext regardless of any
  # `sensitive = true` marking, including db_password/aws_secret_access_key/
  # jwt_secret - sits unencrypted on a local disk with no backup.
  #
  # Left commented rather than live: switching backends requires
  # `terraform init -migrate-state`, a real operational step against your
  # actual state (not something to flip silently), and the S3 bucket/
  # DynamoDB-or-native-lock target need to exist first (bootstrap problem,
  # same as this session's other Terraform-adjacent state-backend setups).
  # Uncomment and fill in real values, then run the migration yourself:
  #
  # backend "s3" {
  #   bucket       = "neobank-terraform-state"
  #   key          = "prod/terraform.tfstate"
  #   region       = "us-east-1"
  #   encrypt      = true
  #   use_lockfile = true  # Terraform 1.10+ native S3 locking, no separate DynamoDB table needed
  # }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

module "networking" {
  source       = "./modules/networking"
  project_name = var.project_name
  environment  = var.environment
}

module "s3" {
  source      = "./modules/s3"
  bucket_name = var.s3_bucket_name
  environment = var.environment
}

module "dynamodb" {
  source       = "./modules/dynamodb"
  project_name = var.project_name
}

module "sns" {
  source       = "./modules/sns"
  project_name = var.project_name
  alert_email  = var.ses_from_email
}

module "sqs" {
  source            = "./modules/sqs"
  project_name      = var.project_name
  sns_txn_topic_arn = module.sns.transaction_notifications_arn
}

module "cognito" {
  source       = "./modules/cognito"
  project_name = var.project_name
  ses_email    = var.ses_from_email
}

module "rds" {
  source             = "./modules/rds"
  project_name       = var.project_name
  db_password        = var.db_password
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  ec2_sg_id          = module.ec2.security_group_id
  lambda_sg_id       = module.lambdas.security_group_id
}

module "ec2" {
  source                = "./modules/ec2"
  project_name          = var.project_name
  key_name              = var.key_name
  vpc_id                = module.networking.vpc_id
  public_subnet_id      = module.networking.public_subnet_id
  instance_type         = var.ec2_instance_type
  db_host               = module.rds.endpoint
  db_password           = var.db_password
  aws_access_key_id     = var.aws_access_key_id
  aws_secret_access_key = var.aws_secret_access_key
  cognito_user_pool_id  = module.cognito.user_pool_id
  cognito_client_id     = module.cognito.client_id
  s3_bucket_name        = var.s3_bucket_name
  sqs_queue_url         = module.sqs.queue_url
  ses_from_email        = var.ses_from_email
  jwt_secret            = var.jwt_secret
  cors_allowed_origins  = var.cors_allowed_origins
  ssh_allowed_cidr      = var.ssh_allowed_cidr
}

module "lambdas" {
  source                  = "./modules/lambdas"
  project_name            = var.project_name
  vpc_id                  = module.networking.vpc_id
  private_subnet_ids      = module.networking.private_subnet_ids
  ledger_queue_arn        = module.sqs.queue_arn
  fraud_queue_arn         = module.sqs.fraud_queue_arn
  analytics_queue_arn     = module.sqs.analytics_queue_arn
  notifications_queue_arn = module.sqs.notifications_queue_arn
  dynamodb_table          = module.dynamodb.table_name
  s3_bucket_name          = var.s3_bucket_name
  sns_fraud_arn           = module.sns.fraud_alerts_arn
  sns_kyc_arn             = module.sns.kyc_notifications_arn
  sns_txn_arn             = module.sns.transaction_notifications_arn
  db_url                  = "jdbc:postgresql://${module.rds.endpoint}/neobank_db"
  db_password             = var.db_password
  aws_access_key_id       = var.aws_access_key_id
  aws_secret_access_key   = var.aws_secret_access_key
  aws_region              = var.aws_region
}

module "api_gateway" {
  source                         = "./modules/api-gateway"
  project_name                   = var.project_name
  transaction_service_invoke_arn = module.lambdas.transaction_service_invoke_arn
  transaction_query_invoke_arn   = module.lambdas.transaction_query_invoke_arn
  transaction_service_arn        = module.lambdas.transaction_service_arn
  transaction_query_arn          = module.lambdas.transaction_query_arn
  cors_allowed_origins           = var.cors_allowed_origins
  cognito_user_pool_arn          = module.cognito.user_pool_arn
}