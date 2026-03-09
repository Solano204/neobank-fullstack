# ─── IAM Role shared by all Lambdas ──────────────────────────
resource "aws_iam_role" "lambda" {
  name = "lambda-${var.project_name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "vpc" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "neobank_permissions" {
  name = "${var.project_name}-lambda-permissions"
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:Query", "dynamodb:Scan"]
        Resource = ["arn:aws:dynamodb:*:*:table/transactions", "arn:aws:dynamodb:*:*:table/transactions/index/*"]
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:SendMessage"]
        Resource = [var.sqs_queue_arn]
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = [var.sns_fraud_arn, var.sns_kyc_arn, var.sns_txn_arn]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = ["arn:aws:s3:::${var.s3_bucket_name}/*"]
      },
      {
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = ["*"]
      },
      {
        Effect   = "Allow"
        Action   = ["rekognition:DetectFaces", "rekognition:CompareFaces"]
        Resource = ["*"]
      }
    ]
  })
}

# ─── Security Group for Lambdas ──────────────────────────────
resource "aws_security_group" "lambda" {
  name   = "${var.project_name}-lambda-sg"
  vpc_id = var.vpc_id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${var.project_name}-lambda-sg" }
}

# ─── Common env vars ──────────────────────────────────────────
locals {
  common_env = {
    DYNAMODB_TABLE      = var.dynamodb_table
    AWS_REGION_NAME     = var.aws_region
    DB_URL              = var.db_url
    DB_USER             = "postgres"
    DB_PASSWORD         = var.db_password
    SNS_FRAUD_TOPIC_ARN = var.sns_fraud_arn
    SNS_KYC_TOPIC_ARN   = var.sns_kyc_arn
    SNS_TXN_TOPIC_ARN   = var.sns_txn_arn
    SQS_QUEUE_URL       = var.sqs_queue_url
    S3_BUCKET           = var.s3_bucket_name
  }
}

# ─── LAMBDA 1: analytics-processor ───────────────────────────
resource "aws_lambda_function" "analytics_processor" {
  function_name = "analytics-processor"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "analytics-processor" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

resource "aws_lambda_event_source_mapping" "analytics_sqs" {
  event_source_arn = var.sqs_queue_arn
  function_name    = aws_lambda_function.analytics_processor.arn
  batch_size       = 10
}

# ─── LAMBDA 2: fraud-checker ─────────────────────────────────
resource "aws_lambda_function" "fraud_checker" {
  function_name = "fraud-checker"
  role          = aws_iam_role.lambda.arn
  handler       = "handler.lambda_handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 256
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "fraud-checker" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

resource "aws_lambda_event_source_mapping" "fraud_sqs" {
  event_source_arn = var.sqs_queue_arn
  function_name    = aws_lambda_function.fraud_checker.arn
  batch_size       = 10
}

# ─── LAMBDA 3: kyc-validator ─────────────────────────────────
resource "aws_lambda_function" "kyc_validator" {
  function_name = "kyc-validator"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 60
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "kyc-validator" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

resource "aws_lambda_permission" "kyc_s3" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.kyc_validator.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = "arn:aws:s3:::${var.s3_bucket_name}"
}

# ─── LAMBDA 4: ledger-writer ─────────────────────────────────
resource "aws_lambda_function" "ledger_writer" {
  function_name = "ledger-writer"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "ledger-writer" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

resource "aws_lambda_event_source_mapping" "ledger_sqs" {
  event_source_arn = var.sqs_queue_arn
  function_name    = aws_lambda_function.ledger_writer.arn
  batch_size       = 10
}

# ─── LAMBDA 5: notification-service ──────────────────────────
resource "aws_lambda_function" "notification_service" {
  function_name = "notification-service"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "notification-service" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

resource "aws_lambda_event_source_mapping" "notification_sqs" {
  event_source_arn = var.sqs_queue_arn
  function_name    = aws_lambda_function.notification_service.arn
  batch_size       = 10
}

# ─── LAMBDA 6: transaction-service ───────────────────────────
resource "aws_lambda_function" "transaction_service" {
  function_name = "transaction-service"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "transaction-service" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

# ─── LAMBDA 7: transaction-query ─────────────────────────────
resource "aws_lambda_function" "transaction_query" {
  function_name = "transaction-query"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "transaction-query" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}

# ─── LAMBDA 8: lex-fulfillment ───────────────────────────────
resource "aws_lambda_function" "lex_fulfillment" {
  function_name = "lex-fulfillment"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "python3.11"
  timeout       = 30
  memory_size   = 512
  filename      = "${path.module}/placeholder.zip"
  environment { variables = local.common_env }
  tags          = { Name = "lex-fulfillment" }
  lifecycle     { ignore_changes = [filename, source_code_hash] }
}