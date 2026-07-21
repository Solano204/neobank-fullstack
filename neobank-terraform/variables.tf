variable "aws_region" {
  default = "us-east-1"
}

variable "project_name" {
  default = "neobank"
}

variable "environment" {
  default = "prod"
}

variable "ec2_instance_type" {
  default = "t3.micro"
}

variable "key_name" {
  description = "EC2 Key Pair name"
}

variable "db_password" {
  sensitive = true
}

variable "aws_access_key_id" {
  sensitive = true
}

variable "aws_secret_access_key" {
  sensitive = true
}

variable "s3_bucket_name" {
  description = "S3 bucket for KYC documents"
}

variable "ses_from_email" {
  description = "Verified SES email address"
}

variable "jwt_secret" {
  sensitive = true
}

variable "cors_allowed_origins" {
  default = "http://localhost:3000"
}

# Doc 7: no default on purpose - see modules/ec2/variables.tf.
variable "ssh_allowed_cidr" {
  description = "CIDR allowed to SSH into the backend EC2 instance - never 0.0.0.0/0"
}