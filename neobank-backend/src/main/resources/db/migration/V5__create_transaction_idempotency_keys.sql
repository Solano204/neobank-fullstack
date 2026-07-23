-- Doc 4/11: transaction-service (Lambda) had no idempotency protection at
-- all on money movement - a client retry after a timeout (the caller can't
-- tell whether the transfer actually succeeded or the response was just
-- lost) would execute a second, distinct transfer. This table backs an
-- Idempotency-Key check-then-act inside the same DB transaction as the
-- transfer itself, so a retried request with the same key returns the
-- original result instead of moving money twice.
CREATE TABLE transaction_idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    from_account VARCHAR(18) NOT NULL,
    to_account VARCHAR(18) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    new_balance DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_idempotency_keys_created_at ON transaction_idempotency_keys(created_at);
