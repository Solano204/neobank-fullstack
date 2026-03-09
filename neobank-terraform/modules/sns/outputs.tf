output "fraud_alerts_arn"              { value = aws_sns_topic.fraud_alerts.arn }
output "kyc_notifications_arn"         { value = aws_sns_topic.kyc_notifications.arn }
output "transaction_notifications_arn" { value = aws_sns_topic.transaction_notifications.arn }