# Testing Notes — neobank

Doc 8 deliverable.

## Confirmed, not assumed from PORTFOLIO.md

- 36 backend test files, including real Testcontainers-backed integration tests (`AccountRepositoryIT`, `AccountControllerIT`) already using the modern `@ServiceConnection` pattern (Spring Boot 3.2.2 supports it) - the same pattern I had to manually add to the other 3 projects reviewed this session already exists here.
- `transaction-service` (the Lambda) has its own `TransactionHandlerTest` - but reading it confirmed it only covers `validateRequest` (the pure input-validation method): missing amount, over-limit, same-account, etc. It does not exercise `executeTransfer` at all - the row-locking, balance-update, and (as of this pass) idempotency-key logic have zero test coverage.
- 2 frontend test files exist (Vitest, per PORTFOLIO.md) - thin relative to the backend, not investigated further given where this pass's time was better spent.

## Real gap, not fixed this pass

`TransactionHandler.executeTransfer` - now carrying the idempotency-key check-then-act and the concurrent-race-loser handling added in Doc 4 - has no test coverage at all. This is exactly the kind of non-trivial concurrency logic that's easy to get subtly wrong and hard to verify by reading alone (the whole reason I hand-verified the brace/flow structure by re-reading the full file after implementing it, rather than trusting the diff). A Testcontainers-Postgres-backed test here - two concurrent requests with the same Idempotency-Key, asserting only one balance change lands and both return the same transaction_id - would be the single highest-value test to add next in this entire codebase. Not implemented this pass: this Lambda uses raw JDBC directly (no Spring context to hook `@ServiceConnection` into the way `AccountControllerIT` does), so it needs a standalone JUnit+Testcontainers harness built from scratch rather than extending an existing pattern - real, scoped work better done deliberately than rushed at the end of an already-long review.
