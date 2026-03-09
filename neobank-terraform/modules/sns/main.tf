resource "aws_sns_topic" "fraud_alerts" {
  name = "${var.project_name}-fraud-alerts"
}

resource "aws_sns_topic" "kyc_notifications" {
  name = "${var.project_name}-kyc-notifications"
}

resource "aws_sns_topic" "transaction_notifications" {
  name = "${var.project_name}-transaction-notifications"
}

resource "aws_sns_topic_subscription" "fraud_email" {
  topic_arn = aws_sns_topic.fraud_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}