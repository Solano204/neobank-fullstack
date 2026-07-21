# SQL Notes — neobank-backend (Postgres/Flyway)

Doc 5 deliverable. This is the strongest SQL baseline of any project reviewed this session - checked rather than assumed.

## Confirmed already correct

- **Every `user_id` foreign key column across all 8 tables has an explicit index** (`idx_accounts_user_id`, `idx_contacts_user_id`, `idx_kyc_documents_user_id`, `idx_notifications_user_id`, `idx_support_tickets_user_id`, `idx_user_sessions_user_id`, `idx_device_tokens_user_id`), matching every `findByUserId`/`findByIdAndUserId` repository method that actually exists. Verified by cross-referencing every repository's query methods against the migrations, not assumed from the table definitions alone.
- **Real Flyway migration history** (`V1`-`V4`), not a destructive re-run-on-boot script or a schemaless push - the one real gap across the other 3 projects this session, absent here.
- **Money columns are `DECIMAL(15,2)`** end to end, matching the `BigDecimal` discipline already confirmed in `TransactionHandler` (Doc 4).
- `updated_at` trigger-maintained on the tables that need it (`contacts`, `kyc_documents`) rather than relying on application code to remember to set it.

## Added this pass

`V5__create_transaction_idempotency_keys.sql` - the table backing the Doc 4 idempotency fix. Everything else in this document's phases (database-per-service isolation - N/A, single monolith backend by design; expand-contract migration discipline; indexing) checked out with nothing further to add.

## Not evaluated

Connection pool sizing (`neobank-backend`'s Hikari config, already explicit: max 10, min-idle 5) and isolation levels beyond what Doc 4 already confirmed (`TRANSACTION_READ_COMMITTED` + `SELECT ... FOR UPDATE` in the transfer path) - both already correct on inspection, no further changes warranted.
