resource "aws_cognito_user_pool" "main" {
  name                     = "${var.project_name}-users"
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_uppercase = true
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "NeoBank - Verify your email"
    email_message        = "Your verification code is {####}"
  }

  tags = { Name = "${var.project_name}-user-pool" }
}

resource "aws_cognito_user_pool_client" "app" {
  name         = "${var.project_name}-app-client"
  user_pool_id = aws_cognito_user_pool.main.id

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]

  prevent_user_existence_errors = "ENABLED"
  access_token_validity         = 60
  refresh_token_validity        = 30

  token_validity_units {
    access_token  = "minutes"
    refresh_token = "days"
  }
}