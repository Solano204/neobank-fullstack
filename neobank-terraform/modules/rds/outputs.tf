output "endpoint" { value = aws_db_instance.postgres.address }
output "sg_id"    { value = aws_security_group.rds.id }