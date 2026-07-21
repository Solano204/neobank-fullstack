# Tracing Notes — neobank

Doc 9 deliverable.

## Backend: Zipkin wired, same pattern as the other 3 projects

`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` added to `neobank-backend`, `management.tracing.sampling.probability=1.0` for local dev, `zipkin` service added to `docker-compose.yml`. Every REST controller call and outbound `WebClient`/AWS SDK call (Cognito, S3, SES) now gets an automatic span.

## Lambdas: deliberately NOT given Zipkin - AWS X-Ray is the right tool here, not a config copy-paste

This is the one place in the whole NEOBANK review where I'm not applying the same Zipkin pattern used everywhere else, and want to be explicit about why rather than silently skip it. Zipkin needs a persistently-running collector service that spans get pushed to - that's a fine model for `neobank-backend` (a long-lived Docker container that can hold an HTTP connection open) but an awkward one for the 8 Lambda functions, which are ephemeral, cold-start on demand, and don't want a network call to a self-hosted collector on every invocation's critical path.

AWS Lambda has native, purpose-built tracing for exactly this shape: **AWS X-Ray**, which auto-instruments Lambda invocations, propagates trace context through SNS/SQS automatically (X-Ray trace headers ride along in message attributes), and requires no persistent collector - the Lambda runtime itself batches and ships trace data. Enabling it is a Terraform-level change (`tracing_config { mode = "Active" }` on each `aws_lambda_function` resource in `neobank-terraform/modules/lambdas`), not an application code change, and would give exactly what this document's principles are after: a continuous trace across `transaction-service` → SNS → `ledger-writer`/`fraud-checker`/`analytics-processor`/`notification-service`, which is precisely the fan-out this whole review spent the most time on (Doc 4/11).

Not implemented this pass: enabling X-Ray means adding IAM permissions (`AWSXRayDaemonWriteAccess` or a scoped equivalent) to each Lambda's execution role, which - like the IAM instance profile finding in Doc 7 - deserves the same "read every call site, scope permissions precisely" treatment rather than a blind addition. Flagging as the correct next step with the exact mechanism, not guessing at the IAM policy here.
