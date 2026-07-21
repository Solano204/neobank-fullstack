# Terraform Notes — neobank-terraform

Doc 7 deliverable. This is a code-review pass, not the migration scenario the Terraform master document is written for (infra here is already IaC, not manually-built resources needing import) - applying the same state/secrets/least-privilege principles as a review checklist instead. No `terraform init`/`plan`/`apply` run - read-only review plus safe code edits, consistent with this whole session's rule of never executing infrastructure changes.

## Fixed: SSH open to the entire internet

`modules/ec2/main.tf`'s security group had port 22 on `cidr_blocks = ["0.0.0.0/0"]` - anyone on the internet could attempt SSH against the backend host. Parameterized as `var.ssh_allowed_cidr` with **no default** (deliberately - there's no CIDR I can safely guess on your behalf, and a wrong-but-present default is worse than forcing an explicit value at apply time). Ports 80/8080 stay open to `0.0.0.0/0`, correctly, since that's the public API surface.

## Documented, not made live: no remote state backend

No `backend` block existed - Terraform state defaults to a local `terraform.tfstate` file: no locking (two concurrent `apply` runs can corrupt state), and every value in this config sits in that file in **plaintext**, regardless of `sensitive = true` (that flag only suppresses CLI output, it doesn't encrypt the state file) - including `db_password`, `aws_secret_access_key`, and `jwt_secret`. Added a commented S3 backend block with real Terraform 1.10+ native-locking syntax (`use_lockfile`, no separate DynamoDB lock table needed) and the exact reasoning for why it's commented rather than live: switching backends requires `terraform init -migrate-state`, a real operation against real state, and the target S3 bucket has to exist first - not something to flip silently in a review pass.

## Flagged, not fixed: static AWS credentials baked into the EC2 instance

`aws_access_key_id`/`aws_secret_access_key` are passed through `templatefile()` into `userdata.sh`, which writes them as plaintext environment variables on the instance (`AWS_ACCESS_KEY_ID=${aws_access_key_id}`). This is long-lived, static IAM credentials sitting on disk on a host that's also exposed on the public internet (ports 80/8080) - if the instance is ever compromised, or if user-data is ever read via the EC2 API by anyone with sufficient IAM access, the credentials extracted aren't scoped to *this instance*, they're a real IAM user's keys with whatever permissions that user has.

**The standard fix**: an **IAM instance profile** (a role attached directly to the EC2 instance) instead of static keys. The AWS SDK's default credential provider chain (which the backend's Cognito/S3/SES clients almost certainly already use unless explicitly configured otherwise - worth confirming) picks up instance-profile credentials automatically via the EC2 metadata service, auto-rotated, with zero static secret ever needing to exist in Terraform variables, user-data, or the instance's disk.

**Not implemented this pass**: writing the actual IAM role/policy means enumerating the *exact* set of AWS actions the backend genuinely calls (specific Cognito admin actions, S3 get/put on the KYC bucket only, SES send, SQS receive on the specific queue) and scoping the policy to exactly that - guessing at this risks either a policy too broad (defeats the purpose) or too narrow (breaks production in a way that's hard to notice until the one code path that needs the missing permission runs). That's a deliberate, traceable piece of work (read every AWS SDK call site in the backend, map each to its minimum IAM action) better done as its own pass than guessed at here.
