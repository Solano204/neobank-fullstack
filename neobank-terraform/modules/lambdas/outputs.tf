output "security_group_id"              { value = aws_security_group.lambda.id }
output "transaction_service_arn"        { value = aws_lambda_function.transaction_service.arn }
output "transaction_query_arn"          { value = aws_lambda_function.transaction_query.arn }
output "transaction_service_invoke_arn" { value = aws_lambda_function.transaction_service.invoke_arn }
output "transaction_query_invoke_arn"   { value = aws_lambda_function.transaction_query.invoke_arn }