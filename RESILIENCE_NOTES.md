# Resilience Notes — neobank

Doc 10 deliverable.

## Confirmed already correct

Rate limiting (`RateLimitFilter`, Guava-backed) already applied globally to `/api/*` via `FilterConfig` - not something I needed to add, unlike anything seen in the other 3 projects this session.

## Fixed: no timeout on any of the 5 backend AWS SDK clients

`S3Client`, `DynamoDbClient`, `CognitoIdentityProviderClient`, and `SesClient` were all built with no `ClientOverrideConfiguration` - the AWS SDK v2's own defaults run into minutes for a genuinely stuck call, far longer than a user-facing request should ever wait. Added a consistent 10s API-call timeout / 5s per-attempt timeout to all 4 (matters most on `CognitoClient` - it sits directly on the login/signup path). `S3Presigner` deliberately left alone: it only computes a signature locally and makes no network call, so a timeout there wouldn't do anything.

## Not evaluated this pass

The Lambda side's own AWS SDK clients (`SnsClient.create()` in `transaction-service`, `DynamoDbClient.create()` in `ledger-writer`/`transaction-query`) use the SDK's zero-arg factory, same gap as the backend had. Not fixed here: this session's time on the Lambda layer went entirely into the Doc 4/11 idempotency and race-condition fixes, which were the load-bearing findings - the same `ClientOverrideConfiguration` pattern applied above to the backend applies identically there, and is the natural next pass once someone's ready to touch that code again.

## Circuit breakers: not applied

Same reasoning as GYM_MOSTER's Doc 9 - timeouts are the correct first layer and are now in place everywhere on the backend that was missing them; circuit breakers need real traffic/failure-rate data to threshold sensibly, which is a "once this is running in front of real users" step, not a blind addition now.
