# Testing strategy addendum — NEOBANK

Unlike every other project in this portfolio, NEOBANK's backend and lambdas already had a **mature, comprehensive test suite** before this pass (30+ use-case unit tests, per-lambda handler tests with real boundary-condition coverage, one controller-level Testcontainers integration test). The "build from zero" assumption behind the testing-strategy prompt doesn't apply here - this addendum documents an **audit + gap-fill**, not a rebuild. Frontend has minimal coverage (`lib/utils.test.ts`, `lib/api/transactions.test.ts`) - not expanded in this pass, flagged below.

**Verification status**: authored, not executed, per instruction - same caveat as every other project in this pass. `AuthControllerIT` needs `mvn test` (Testcontainers Postgres) before being trusted.

## What was audited

- All 7 Lambda modules: each already has a real test file with genuine edge-case coverage (e.g. `TransactionHandlerTest` tests the exact daily-limit boundary, not just "valid input works"). No gaps found worth adding to.
- Backend use-case layer (`neobank/application/usecase/**`): auth, account, contact, kyc, support, user - all have real unit tests already. No gaps found.
- Backend controller layer: only `AccountController` had an integration test (`AccountControllerIT`). The other 8 controllers - including `AuthController` and `SecurityController`, the most security-relevant ones - had zero HTTP-level test coverage.

## What was added

**`AuthControllerIT.java`** (9 tests), matching `AccountControllerIT`'s established pattern (Testcontainers Postgres, `@MockBean` for the AWS adapters). Filled the real gap: nothing tested the actual HTTP wiring (routing, `@Valid` validation, which endpoints are genuinely public) for the auth surface.

**Found and fixed while writing it**: `SecurityConfig.java` blanket-`permitAll()`'d the entire `/api/auth/**` path, which also covers `/api/auth/logout` and `/api/auth/change-password` - both of which require an authenticated principal internally (`@AuthenticationPrincipal UserPrincipal` in `logout()`, a required `Authorization` header in `changePassword()`). With no token present, `logout()` NPEs on a null principal and `changePassword()` throws an unhandled `MissingRequestHeaderException` - neither is caught by any specific `GlobalExceptionHandler` handler, so both fell through to the generic `Exception.class` → 500 handler instead of a proper 401/403. This is a correctness/error-handling bug, not a data-exposure vulnerability - both use cases require a real authenticated principal to do anything privileged, so an unauthenticated caller never reached any actual account/money logic, just got the wrong status code back. Fixed by narrowing `permitAll()` to exactly the sub-paths that are genuinely public (signup, login, verify-email, resend-code, refresh-token, forgot-password, reset-password). `AuthControllerIT`'s `logout_noAuthorizationHeaderAtAll_returns403NotA500` / `changePassword_noAuthorizationHeaderAtAll_returns403NotA500` are the direct regression tests.

## Not done, flagged

1. Frontend: minimal test coverage (2 files). Given the backend/lambdas were already solid, this is the frontend's actual gap - auth pages/flows (login, signup, password reset UI) have no component tests. Not attempted in this pass given time spent auditing the already-large backend suite; recommended next.
2. Mutation testing (PIT) - not set up, matching the "don't execute" constraint for this whole pass; the existing suite's real quality (see `TransactionHandlerTest`'s boundary tests) suggests it would likely score well, but that's exactly the kind of claim mutation testing exists to verify, not assume.
3. `SecurityController` - referenced during the audit, not read in detail or tested in this pass; worth a follow-up look given its name suggests it's also security-relevant.

## Pass 2 — full backend + frontend gap-fill

Follow-up pass closing every gap flagged above: `SecurityController`, the remaining 6 unaudited controllers, the Lambda test that had drifted from production code, and the frontend's near-total lack of coverage. Verification status changed for this pass: `mvn verify` and `npx vitest run` were both actually executed (not just authored), matching the rest of the portfolio's Pass 2 convention.

### Backend bugs found and fixed

- **`application.yml` had duplicate top-level `server:`/`management:` keys.** SnakeYAML rejects a YAML file with the same key twice, so this was breaking Spring context loading for every `@SpringBootTest`/`@WebMvcTest` - and would have broken the real running app the same way. Merged into single consolidated blocks.
- **`AuthController.logout()` always NPE'd.** It called `logoutUseCase.execute(accessToken, null)` - a hardcoded `null` in place of the real user. `LogoutUseCase` took a `User` entity and called `.getId()` on it, so every real logout request threw. Changed `LogoutUseCase.execute` to take `UUID userId` directly (matching the rest of the use-case layer's convention) and wired the controller to pass `userPrincipal.getId()`.
- **`SecurityService.terminateSession()` threw a bare `RuntimeException`** on both "not found" and "belongs to another user" - no `GlobalExceptionHandler` entry maps `RuntimeException`, so both cases fell through to a generic 500 instead of 404/403. Same bug class as the Pass 1 `AuthController` finding. Fixed using the existing `ResourceNotFoundException`/`UnauthorizedException` domain exceptions. `SecurityControllerIT` has a direct regression test (`terminateSession_belongingToAnotherUser_returns401NotA500_andIsNotDeleted`).
- **`SupportControllerIT` initially assumed `/api/support/faq` was public** - it isn't; `SecurityConfig`'s `permitAll()` list doesn't cover it despite the handler having no obvious auth requirement. Fixed the test's assumption rather than the code (this is intentional, not a bug), with a comment explaining the surprise.
- **`transaction-query` Lambda's test referenced a removed `getDoubleValue()` method.** Production code had already migrated to `getBigDecimalValue()` for precision safety; the test wasn't updated and would not compile. Rewrote with `ArgumentCaptor`/`BigDecimal` precision assertions.
- **`GenerateUploadUrlUseCaseTest` hardcoded the old, insecure S3-key format** (predictable key derived from user input) while production code had already moved to a server-generated UUID key. Fixed with an `ArgumentCaptor` + regex match on the new key shape, and added edge-case tests (uniqueness, no-extension, unsupported-extension, case-insensitivity) that the old test never covered.
- **`UserControllerIT` settings tests 404'd** because `UserSettings` is provisioned as a separate row at signup time, not lazily created on first GET - tests were building users directly via the repository (bypassing signup), so no settings row existed. Fixed by seeding `UserSettings` explicitly, and kept a dedicated test asserting the 404-when-missing behavior as intentional.

### Backend coverage added

- New unit tests: `SecurityServiceTest` (8), `GetBalanceForecastUseCaseTest`, `GetNotificationsUseCaseTest`, `MarkNotificationReadUseCaseTest`, `MarkAllNotificationsReadUseCaseTest`, `DeleteNotificationUseCaseTest`, `RegisterDeviceTokenUseCaseTest`, `GetSupportTicketsUseCaseTest` (20 total) - closing the 7 untested use cases a `comm -23` diff of all use-case classes vs. all `*Test.java` files surfaced (a systematic gap audit, since the Pass 1 "no gaps found" claim for the use-case layer turned out to be incomplete).
- New controller integration tests (Testcontainers Postgres, matching the established `AccountControllerIT`/`AuthControllerIT` pattern): `SecurityControllerIT` (7), `ContactControllerIT` (9), `KycControllerIT` (8), `NotificationControllerIT` (9), `SupportControllerIT` (7), `UserControllerIT` (8), `AnalyticsControllerIT` (5). Every controller in the app now has HTTP-level coverage.

### Frontend: went from 2 test files to full coverage

Frontend had essentially nothing (`lib/utils.test.ts`, `lib/api/transactions.test.ts`) - now every lib module, UI component, layout, and page has real tests: 50 test files, 313 tests, all passing; `tsc --noEmit` clean.

- **Infrastructure**: added `@vitejs/plugin-react`, `globals: true`, and a `vitest.setup.ts` (jsdom-guarded `jest-dom` matchers + `matchMedia` polyfill) - none of this existed before.
- **Real bug found and fixed**: `lib/api/contacts.ts`'s `add()` sent `{ account_number, nickname }` (snake_case) but the backend's `AddContactRequest` DTO binds plain camelCase Jackson with no naming-strategy override anywhere in the codebase - confirmed by grep. Every real "add contact" call would 400. Fixed the source, and `ContactControllerIT` (added this pass) is the regression test that would have caught it had it existed first.
- **Lib layer** (13 files, 82+ tests): every `lib/api/*.ts` wrapper, the axios client's interceptor chain (token attachment, 401-refresh-and-retry with request queuing for concurrent 401s, using a *separate* `MockAdapter` on the raw `axios` import since the refresh call deliberately bypasses the instance to avoid interceptor recursion), the Zustand `authStore`, and `lib/utils.ts`.
- **Components**: `Badge`, `Button`, `Card`, `Input`, `Modal`, `Header`, `Sidebar`.
- **Every page and layout under `app/`**: auth flow (login, signup, forgot/reset password, verify-email - 5 pages), dashboard, accounts, transactions, transfer, kyc, contacts, analytics, support (chat), security, settings, notifications, plus every route's `layout.tsx` (10 of these are byte-identical `RouteLayout` wrappers - one canonical test suite copied across all 10 rather than hand-writing near-duplicates - and the distinct `dashboard/layout.tsx` loading state), the root `app/layout.tsx` (with `next/font/google` stubbed, since it depends on the Next.js SWC compiler and throws under plain Vitest), and `app/page.tsx`'s redirect.
- **Notable pattern**: `securityApi`'s MFA and fraud-alert-feed methods are explicit `notImplemented()` stubs today (documented in `lib/api/security.ts` - no backend exists yet for Cognito MFA or a per-user fraud feed). Tests cover both realities: the current production behavior (those calls reject, so the alerts section and MFA modal's toast surface an error) and the success path (so the tests keep working once a real backend lands, without needing a rewrite).
- **jsdom gotchas worked around**: native HTML5 `required`/`type="email"` constraint validation blocks the `submit` event before React's `onSubmit` fires, making some "all fields empty" validation-message tests unreachable via a realistic DOM interaction (redesigned around what's actually reachable, e.g. a too-short-but-non-empty password); `scrollIntoView` isn't implemented in jsdom (stubbed for the support chat page); recharts' `ResponsiveContainer` needs a real `ResizeObserver`/layout to render children (analytics page tests stub `recharts` with passthrough components rather than fight the library's internals - the goal is testing the page's own logic, not recharts).

### Not done, flagged

1. Mutation testing (PIT) - still not run, same rationale as Pass 1.
2. `lib/api/accounts.ts::getStatement`, `lib/api/security.ts`'s MFA/alerts methods, and a couple of `lib/api/transactions.ts` methods are explicit `notImplemented()` stubs (no backend route exists) - tested for their current reject-always behavior where relevant, but there's no real integration to verify once a backend lands.
3. ESLint: `npx eslint app lib components` is clean except for pre-existing warnings unrelated to this pass (one unused `Modal` import in `app/transfer/page.tsx`, and unused-parameter warnings on the intentionally-named `_id`/`_reason`/etc. stub parameters across the `notImplemented()` functions) - not touched, out of scope for a test-coverage pass.
