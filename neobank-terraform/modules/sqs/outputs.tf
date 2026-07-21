output "queue_url" { value = aws_sqs_queue.transactions.url }
output "queue_arn" { value = aws_sqs_queue.transactions.arn }
output "fraud_queue_arn" { value = aws_sqs_queue.transactions_fraud.arn }
output "analytics_queue_arn" { value = aws_sqs_queue.transactions_analytics.arn }
output "notifications_queue_arn" { value = aws_sqs_queue.transactions_notifications.arn }
