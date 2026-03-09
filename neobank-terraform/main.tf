terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
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

module "sqs" {
  source       = "./modules/sqs"
  project_name = var.project_name
}

module "sns" {
  source       = "./modules/sns"
  project_name = var.project_name
  alert_email  = var.ses_from_email
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
}

module "lambdas" {
  source                = "./modules/lambdas"
  project_name          = var.project_name
  vpc_id                = module.networking.vpc_id
  private_subnet_ids    = module.networking.private_subnet_ids
  sqs_queue_arn         = module.sqs.queue_arn
  sqs_queue_url         = module.sqs.queue_url
  dynamodb_table        = module.dynamodb.table_name
  s3_bucket_name        = var.s3_bucket_name
  sns_fraud_arn         = module.sns.fraud_alerts_arn
  sns_kyc_arn           = module.sns.kyc_notifications_arn
  sns_txn_arn           = module.sns.transaction_notifications_arn
  db_url                = "jdbc:postgresql://${module.rds.endpoint}/neobank_db"
  db_password           = var.db_password
  aws_access_key_id     = var.aws_access_key_id
  aws_secret_access_key = var.aws_secret_access_key
  aws_region            = var.aws_region
}

module "api_gateway" {
  source                         = "./modules/api-gateway"
  project_name                   = var.project_name
  transaction_service_invoke_arn = module.lambdas.transaction_service_invoke_arn
  transaction_query_invoke_arn   = module.lambdas.transaction_query_invoke_arn
  transaction_service_arn        = module.lambdas.transaction_service_arn
  transaction_query_arn          = module.lambdas.transaction_query_arn
  cors_allowed_origins           = var.cors_allowed_origins
}