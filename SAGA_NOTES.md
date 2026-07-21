# Saga Deep Audit — neobank transaction pipeline

Doc 11 deliverable. The most saga-applicable project reviewed this session: `transaction-service` (pivot: moves real money) fans out via SNS to `ledger-writer`, `fraud-checker`, `analytics-processor`, `notification-service` in parallel with no ordering guarantee between them.

## Step classification

| Step | Type |
|---|---|
| `transaction-service`: row-lock + debit sender + credit recipient (Postgres) | **Pivot** - money has moved, irreversible without a new compensating write |
| `ledger-writer`: DynamoDB ledger record | Compensable in principle (a record, not money) |
| `fraud-checker`: score + freeze | **Intended as compensating, but isn't one** - see below |
| `analytics-processor` / `notification-service` | Non-critical side effects, no compensation needed |

## Critical, flagged (not fixed this pass): "freeze" doesn't actually reverse anything

`fraud-checker`'s `freeze_transaction` writes `status = FROZEN_FRAUD` to the **DynamoDB ledger record only**. It never touches the `accounts.balance`/`available_balance` columns in Postgres - the columns `transaction-service` already debited/credited *before* fraud-checker ever runs (SNS publish happens after the Postgres commit, per Doc 4). PORTFOLIO.md's own framing - "freezes the transaction... above threshold" - reads as if this prevents or reverses the transfer. It doesn't: by the time a transaction scores >= 0.75 and gets marked `FROZEN_FRAUD`, the recipient's `available_balance` already reflects the funds and there is nothing in `AccountController`/the balance-check code path that treats a `FROZEN_FRAUD` *transaction* as a reason to hold or reverse those specific funds. (Account-level freeze - `FreezeAccountUseCase` - is a separate, unrelated mechanism: a human explicitly freezing an entire account, not an automated per-transaction reversal.)

This is exactly the gap this document's compensation-design phase exists to catch: a step that's *named* like a compensating transaction but is actually just a status annotation with no real compensating action behind it. For a transaction genuinely flagged high-risk, the money has already moved and stays moved.

**Why not fixed here**: a real compensating transaction (reverse the specific transfer: credit the sender back, debit the recipient) needs to handle a case fraud detection is inherently exposed to - the recipient may have already spent or withdrawn the funds by the time the async fraud check completes seconds later, since nothing blocks the recipient's account between the transfer committing and the fraud score landing. A correct fix has to decide, deliberately: does the reversal simply fail/alert-for-manual-review if the recipient's balance is now insufficient to claw back? Does the *recipient's* account get frozen (the mechanism that already exists) as the fallback? That's a real product/risk decision, not a mechanical code change, and guessing at it in a review pass risks encoding the wrong policy into a banking app's fraud response. Flagging with the precise mechanism and the precise decision point, not a guessed implementation.

## Confirmed correct (Doc 4/6 already verified these, cross-referenced here rather than re-audited)

- Idempotency on the pivot transaction itself - fixed in Doc 4.
- The ledger/fraud race on the same DynamoDB item - correctly handled via conditional `UpdateItem`, verified in Doc 4.
- Partial-batch-failure reporting on every SQS consumer (`ledger-writer`, `fraud-checker`) - each reports only the specific failed message back via `batchItemFailures`, not the whole batch.

## Timeouts

No per-step timeout exists for "how long should a transaction wait in an unscored state before something notices fraud-checker never ran" - given fraud-checker runs synchronously-enough in practice (SQS-triggered within seconds), and given the deeper issue above (freeze not being a real reversal) is the higher-priority fix, not pursuing a timeout/monitoring layer on top of a compensation mechanism that doesn't yet work.
