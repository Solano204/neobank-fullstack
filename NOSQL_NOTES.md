# NoSQL Notes — neobank (DynamoDB transaction ledger)

Doc 6 deliverable.

## Confirmed already correct: key design

`transactions` table (`transaction_id` PK) with two GSIs (`from_account`+`timestamp`, `to_account`+`timestamp`) is a textbook patterns-first design, verified against the actual access pattern in `transaction-query`: an account can be either side of a transfer, so both GSIs get queried and the results merged/sorted/limited in application code (`queryTransactions`) - a real, correct instance of the "query both GSIs, merge, dedupe/sort" pattern this document exists to check for, not something to redesign.

## Fixed: money read back as `double` at the query boundary

`transaction-query`'s `getDoubleValue` converted the DynamoDB `amount`/`balance_after` attributes to `double` before returning them in the API response - undoing, at the read side, the exact float-precision problem `TransactionHandler` and `ledger-writer` were both deliberately written with `BigDecimal` to avoid (both have their own comments explaining why). The actual stored balance was never at risk (Postgres and DynamoDB both keep the authoritative values as `DECIMAL`/exact-precision numeric strings) - this was specifically a display-layer bug: a user's transaction history could show drifted amounts. Changed to `BigDecimal` throughout, matching the discipline already established on the write side.

## CAP / consistency

DynamoDB here is correctly used for what it's good at (a high-write, simple-key-pattern ledger) while Postgres remains the source of truth for account balances and ownership - no gap in that split. `PAY_PER_REQUEST` billing mode is a reasonable choice for genuinely bursty, hard-to-forecast transaction volume rather than a static provisioned-capacity guess.
