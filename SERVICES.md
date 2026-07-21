# NEOBANK — Service Descriptions

## neobank-backend (Spring Boot, Java 21)
The core REST API, deployed as a Docker container to EC2. Hexagonal architecture: `domain` (entities, enums, repository interfaces), `application` (use cases + services, one class per operation), `infrastructure` (JPA/Postgres adapters, Cognito/S3/SES AWS adapters, security, DynamoDB read adapter for analytics), `presentation` (REST controllers). Auth is Cognito-issued JWT validated via Spring Security + `NimbusJwtDecoder`. Nine controllers:
- **AuthController** — signup, login, email verification, password reset/change, token refresh, logout (Cognito-backed).
- **AccountController** — list/get accounts, balance, freeze/unfreeze.
- **ContactController** — saved recipients (add/list/delete/favorite).
- **KycController** — presigned S3 upload URL, submit-for-verification, status, delete document.
- **UserController** — profile and settings CRUD, account deletion.
- **AnalyticsController** — spending breakdown and balance forecast, computed live from DynamoDB transaction history (not mocked).
- **NotificationController** — in-app notification feed, backed by a Postgres table populated by the `notification-service` Lambda.
- **SecurityController** — active session list and session termination.
- **SupportController** — FAQ, ticket creation/listing (Postgres-backed), and a keyword-classified chat assistant that answers balance/transfer/KYC/password/limit questions from real account data.

Database: PostgreSQL via Flyway migrations (`users`, `accounts`, `contacts`, `kyc_documents`, `notifications`, `device_tokens`, `support_tickets`).

## neobank-lambdas (8 functions)
| Function | Runtime | Trigger | Responsibility |
|---|---|---|---|
| `transaction-service` | Java 17 | API Gateway (Cognito-authorized) | Executes a transfer: row-locks the sender's account, moves funds with `BigDecimal`, commits to Postgres, publishes the event to SNS. |
| `transaction-query` | Java 17 | API Gateway (Cognito-authorized) | Reads a user's transaction history from DynamoDB across both the sent and received GSIs. |
| `ledger-writer` | Java 17 | SQS (SNS fan-out) | Writes the canonical DynamoDB ledger record for each transaction via a conditional partial update, so it can't clobber a concurrent fraud freeze. |
| `fraud-checker` | Python 3.11 | SQS (SNS fan-out) | Rule-based risk scoring (amount, time of day, weekend, round-number heuristics); freezes the transaction and alerts via SNS above threshold. |
| `analytics-processor` | Java 17 | SQS (SNS fan-out) | Publishes per-transaction custom metrics to CloudWatch (count, volume, success rate). |
| `notification-service` | Java 17 | SQS (SNS fan-out) | Sends a transaction alert via SNS and writes a notification row to Postgres for both sender and recipient. |
| `kyc-validator` | Java 17 | S3 (object created) | Runs AWS Rekognition face/quality checks on an uploaded KYC document, updates Postgres, notifies the user. |
| `lex-fulfillment` | Java 17 | Lex bot fulfillment | Answers balance/transaction-status/history/fraud-report/help intents by querying Postgres and DynamoDB directly. |

## neobank-frontend (Next.js 15 / React 19 / TypeScript)
App-router SPA with 18 routes (dashboard, accounts, transfer, transactions, contacts, KYC, analytics, notifications, security, support, settings, auth flows). Two API clients: one for the Spring backend, one for the Lambda/API-Gateway transaction endpoints — each with its own base URL and token-refresh interceptor. Zustand for auth state, Recharts for analytics visualizations, Tailwind CSS v4 for styling.

## neobank-terraform
Provisions the full AWS footprint: VPC/subnets, RDS (Postgres), EC2 (backend host), S3 (KYC documents), DynamoDB (`transactions` table + 2 GSIs), SNS (transaction/fraud/KYC topics), SQS (4 queues + 4 DLQs), Cognito user pool, API Gateway (Cognito authorizer), and the 8 Lambda functions with their IAM role, VPC attachment (where they need RDS), and event source mappings.

## jmeter
Two load-test plans: one drives the Spring REST API end-to-end (signup → login → accounts → contacts → KYC → analytics → notifications → support → security), the other drives the Cognito-authorized transaction Lambdas directly through API Gateway.
