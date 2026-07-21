# Async Event Design Notes — neobank (SNS/SQS fan-out)

Doc 4 deliverable. No Kafka here - `transaction-service` publishes one SNS message per transfer, fanned out to 4 SQS-backed Lambda consumers (`ledger-writer`, `fraud-checker`, `analytics-processor`, `notification-service`), each with its own DLQ. Applying the event-design principles (taxonomy, domain/integration separation, tolerant reader, race handling) rather than the Kafka-specific document, since the transport is AWS pub-sub, not Kafka.

## Critical, fixed: no idempotency on the money-movement request itself

This is the most important finding of the whole NEOBANK review. `transaction-service` (the Lambda executing transfers) had zero protection against a client retry: a caller that times out waiting for a response - genuinely unable to tell whether the transfer succeeded or the response was just lost - would, on retry, execute a **second, distinct transfer**. Real money moved twice. Everything else about this Lambda's money-movement code was careful (row locking, `BigDecimal`, ownership checks) except this.

Fixed with the same Idempotency-Key pattern used elsewhere this session, adapted for a Lambda with direct JDBC access: a new `transaction_idempotency_keys` table (Flyway `V5`), checked at the start of the same DB transaction that does the transfer, with the dedup record inserted right before commit rather than after - so a crash between "transfer committed" and "dedup record written" can't happen (they're the same atomic commit). Concurrent requests with the *same* key racing each other are also handled: the loser's `INSERT` hits the table's primary key and fails with Postgres's `23505` (unique_violation), at which point its balance-changing updates are rolled back entirely and it returns the winner's already-committed result instead of erroring.

Also fixed in the same pass, found while implementing this: the SNS publish step now only fires on a genuinely fresh transfer, not on a deduplicated retry - the original code would have re-published to SNS unconditionally, causing `ledger-writer`/`fraud-checker`/`analytics-processor`/`notification-service` to all reprocess the same underlying transaction on every retry even after the transfer itself was correctly deduplicated.

## Also fixed: transaction ID collisions

`transactionId` was generated as `"txn_" + System.currentTimeMillis() + "_" + last-4-digits-of-account` - millisecond resolution only, so two transfers from the same account within the same millisecond (plausible under real concurrent or scripted load) would produce the *same* ID. `ledger-writer` keys its conditional DynamoDB `UpdateItem` off this exact ID - a collision there means one transaction's ledger write could land on top of an unrelated one's item. Replaced the account-digits suffix with a `SecureRandom`-backed one.

## Confirmed correct (verified by reading the code, not assumed from PORTFOLIO.md)

`ledger-writer`'s conditional partial `UpdateItem` - the exact race PORTFOLIO.md describes (two Lambdas from different SQS queues, no ordering guarantee, both writing to the same DynamoDB item) is handled correctly: a guarded `conditionExpression` prevents overwriting a fraud freeze, with a `ConditionalCheckFailedException` fallback path that writes every other ledger field without touching `status` if `fraud-checker` already froze the transaction. Partial-batch-failure reporting (`SQSBatchResponse.BatchItemFailure`) is also correctly implemented - a single bad message in a batch gets retried/DLQ'd without reprocessing the rest of the batch. Both claims in PORTFOLIO.md checked out against the actual code.

## Minor, not fixed

`LedgerWriterHandler.getBigDecimal`'s fallback branch (`BigDecimal.valueOf(number.doubleValue())`) still round-trips through `double` if the incoming value isn't already a `BigDecimal` - currently unreachable in practice since the producer sends a JSON number and the consumer's `ObjectMapper` is configured with `USE_BIG_DECIMAL_FOR_FLOATS`, but it's a latent landmine if either side's serialization ever changes without the other being updated to match. Not changed - throwing instead of falling back would be the more defensive choice, but risks breaking a path I can't fully rule out being intentionally reachable without deeper tracing than this pass covered.
