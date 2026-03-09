resource "aws_sqs_queue" "transactions" {
  name                       = "${var.project_name}-transactions"
  visibility_timeout_seconds = 90
  message_retention_seconds  = 86400
  tags = { Name = "${var.project_name}-transactions" }
}