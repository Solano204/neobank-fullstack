resource "aws_dynamodb_table" "transactions" {
  name         = "transactions"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "transaction_id"

  attribute {
    name = "transaction_id"
    type = "S"
  }

  attribute {
    name = "from_account"
    type = "S"
  }

  attribute {
    name = "to_account"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "N"
  }

  global_secondary_index {
    name            = "from_account-timestamp-index"
    hash_key        = "from_account"
    range_key       = "timestamp"
    projection_type = "ALL"
  }

  # Without this, an account that only ever received money has no way to
  # show up in its own transaction history/query -- the from_account index
  # alone only ever finds transfers a given account sent.
  global_secondary_index {
    name            = "to_account-timestamp-index"
    hash_key        = "to_account"
    range_key       = "timestamp"
    projection_type = "ALL"
  }

  tags = { Name = "${var.project_name}-transactions" }
}