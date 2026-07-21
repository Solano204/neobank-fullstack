# ─── Dead-letter queues ──────────────────────────────────────
# Every consumer used to catch-and-log per-record errors without ever
# failing the invocation, so a permanently broken message (bad payload,
# transient DB outage) was silently dropped, never retried, never visible
# anywhere. With ReportBatchItemFailures (set on each event source mapping
# below) + maxReceiveCount, a message that keeps failing lands here instead
# of vanishing.
resource "aws_sqs_queue" "transactions_dlq" {
  name                      = "${var.project_name}-transactions-dlq"
  message_retention_seconds = 1209600 # 14 days
  tags                      = { Name = "${var.project_name}-transactions-dlq" }
}

resource "aws_sqs_queue" "transactions_fraud_dlq" {
  name                      = "${var.project_name}-transactions-fraud-dlq"
  message_retention_seconds = 1209600
  tags                      = { Name = "${var.project_name}-transactions-fraud-dlq" }
}

resource "aws_sqs_queue" "transactions_analytics_dlq" {
  name                      = "${var.project_name}-transactions-analytics-dlq"
  message_retention_seconds = 1209600
  tags                      = { Name = "${var.project_name}-transactions-analytics-dlq" }
}

resource "aws_sqs_queue" "transactions_notifications_dlq" {
  name                      = "${var.project_name}-transactions-notifications-dlq"
  message_retention_seconds = 1209600
  tags                      = { Name = "${var.project_name}-transactions-notifications-dlq" }
}

resource "aws_sqs_queue" "transactions" {
  name                       = "${var.project_name}-transactions"
  visibility_timeout_seconds = 90
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transactions_dlq.arn
    maxReceiveCount     = 5
  })
  tags = { Name = "${var.project_name}-transactions" }
}

resource "aws_sqs_queue" "transactions_fraud" {
  name                       = "${var.project_name}-transactions-fraud"
  visibility_timeout_seconds = 90
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transactions_fraud_dlq.arn
    maxReceiveCount     = 5
  })
  tags = { Name = "${var.project_name}-transactions-fraud" }
}

resource "aws_sqs_queue" "transactions_analytics" {
  name                       = "${var.project_name}-transactions-analytics"
  visibility_timeout_seconds = 90
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transactions_analytics_dlq.arn
    maxReceiveCount     = 5
  })
  tags = { Name = "${var.project_name}-transactions-analytics" }
}

resource "aws_sqs_queue" "transactions_notifications" {
  name                       = "${var.project_name}-transactions-notifications"
  visibility_timeout_seconds = 90
  message_retention_seconds  = 86400
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transactions_notifications_dlq.arn
    maxReceiveCount     = 5
  })
  tags = { Name = "${var.project_name}-transactions-notifications" }
}

# Fan-out: transaction-service publishes once to the shared SNS topic, and each
# consumer gets its own queue/subscription so every transaction reaches ledger,
# fraud, analytics AND notifications -- instead of the 4 lambdas competing for
# messages off a single queue (only one consumer ever saw a given message).
locals {
  fanout_queues = {
    ledger        = aws_sqs_queue.transactions
    fraud         = aws_sqs_queue.transactions_fraud
    analytics     = aws_sqs_queue.transactions_analytics
    notifications = aws_sqs_queue.transactions_notifications
  }
}

resource "aws_sqs_queue_policy" "allow_sns" {
  for_each  = local.fanout_queues
  queue_url = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = each.value.arn
      Condition = { ArnEquals = { "aws:SourceArn" = var.sns_txn_topic_arn } }
    }]
  })
}

resource "aws_sns_topic_subscription" "fanout" {
  for_each  = local.fanout_queues
  topic_arn = var.sns_txn_topic_arn
  protocol  = "sqs"
  endpoint  = each.value.arn
  # Without this, SQS gets the SNS envelope (Message as a nested JSON string)
  # instead of the original payload -- every consumer's `objectMapper.readValue`
  # on the raw message body would break.
  raw_message_delivery = true
}
