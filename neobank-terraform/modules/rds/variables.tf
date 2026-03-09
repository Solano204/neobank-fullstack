variable "project_name" {}
variable "db_password"        { sensitive = true }
variable "vpc_id" {}
variable "private_subnet_ids" { type = list(string) }
variable "ec2_sg_id" {}
variable "lambda_sg_id" {}