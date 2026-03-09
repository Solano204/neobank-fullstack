output "public_ip"         { value = aws_instance.backend.public_ip }
output "security_group_id" { value = aws_security_group.ec2.id }
output "instance_id"       { value = aws_instance.backend.id }