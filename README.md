# Benefits Portal (AWS)

A citizen-facing unemployment-benefits portal: claimants file a claim online and get a
weekly benefit amount on the spot, caseworkers review it, claimants can appeal a denial,
and approved claimants certify each week to get paid. Built as a portfolio project targeting government-services /
GovTech internships (Maximus and similar contractors run exactly this kind of system
for state unemployment insurance and benefits programs).

## Why this project

State unemployment-insurance systems are large, old, and slowly being modernized —
which is the actual business a lot of government-technology contractors are in. This
project is a small, honest version of that same shape: a Java/SQL app (the stack these
internships actually list as a requirement) that also gets containerized and deployed
to AWS with infrastructure-as-code, instead of stopping at "runs on my machine."

## What it does

It follows a claim the way a state unemployment office does, from filing to weekly payments.

**For claimants**

- **File a claim** with their employer, last day worked, reason for separation, and
  average weekly wage. The app calculates **monetary eligibility** and a **weekly benefit
  amount** on the spot (50% wage replacement, floored at $50/week, capped at $500/week).
  A disqualifying separation reason (fired for misconduct, quit without cause) zeroes the
  benefit out.
- Get a random **confirmation number** (like `UI-7K3F-9QX2`) and use it with their email
  to **check their claim** later: status in plain English, the reason for a denial, and a
  timeline.
- **Appeal a denial** once, within 30 days of the decision.
- **Certify every week** once approved: able and available to work? turned down a job?
  how many employers contacted? any earnings? Each week is then paid, partly paid, or not
  paid, following common state rules (see below).
- **Upload proof** such as pay stubs, layoff letters, or ID (PDF, PNG, JPEG up to 5 MB).

**For staff**

- **Separate accounts** for caseworkers and supervisors, stored in the database with
  BCrypt-hashed passwords. Supervisors add and switch off accounts from a Staff page.
- A **claims queue** with status filters and a **Flagged** filter, and a page per claim
  with details, documents, weekly certifications, payments, and history.
- **Status rules**: `FILED → UNDER_REVIEW → APPROVED / DENIED`, and
  `DENIED → APPEALED → APPROVED / DENIED`. Nothing can skip a step, and a denial must
  say why.
- **Separation of duties**: only a supervisor can decide an appeal, and never on a claim
  they denied themselves.
- **Fraud checks** on every new claim: another open claim with the same email, the same
  name and employer under a different email, or an unusually high wage. A flagged claim
  can't be approved until someone clears the flag with a note.
- An **audit history** of every change: who did what, and when. History rows are
  append-only (no updates, no deletes).

**Weekly payment rules** (all configurable in `application.yml`)

| Rule | Default |
| --- | --- |
| Must be able and available to work, and must not refuse a job offer | on |
| Job contacts needed each week (work search) | 3 |
| Earnings ignored before the payment is reduced ("disregard") | 25% of the weekly benefit |
| First payable week is an unpaid waiting week | on |
| Maximum weeks of benefits per claim | 26 |
| Days to appeal a denial | 30 |

Example: a $400 weekly benefit and $160 of part-time earnings. $100 (25%) is ignored, so
the payment drops by $60 to $340.

**API**

A JSON API sits behind the same service layer as the web pages, so every rule applies to
both:

| Method and path | Who |
| --- | --- |
| `POST /api/claims` | public |
| `GET /api/claims`, `GET /api/claims/{id}` | staff |
| `PATCH /api/claims/{id}/status` | staff (409 if a rule is broken, 403 if the role isn't allowed) |
| `GET /api/claims/{id}/history`, `/certifications`, `/payments`, `/documents`, `/flags` | staff |

## Architecture

```
                 ┌────────────────────┐ ──▶ MySQL (RDS in prod,
  Claimant ────▶ │   Spring Boot app   │      docker-compose locally)
  Staff    ────▶ │  (Thymeleaf + REST) │
                 └────────────────────┘ ──▶ Uploaded documents (S3 in prod,
                                              a Docker volume locally)
                          │
                 Docker image, built by
                 GitHub Actions CI on every push
                          │
                          ▼
              Terraform provisions: EC2 (runs the
              container) + RDS MySQL + a private S3
              bucket + an IAM role + security groups,
              inside the AWS account's default VPC
```

## Tech stack

Java 21 · Spring Boot 3 (Web, Data JPA, Security, Thymeleaf, Validation) · MySQL 8 ·
AWS SDK for Java v2 (S3) · Docker · Terraform (AWS provider) · GitHub Actions

## Running it locally

Requires Docker and Docker Compose.

```bash
git clone https://github.com/sayyednx/benefits-portal-aws.git
cd benefits-portal-aws
cp .env.example .env   # then set your own ADMIN_PASSWORD in .env
docker compose up --build
```

Then open:

- `http://localhost:8080/` — file a claim
- `http://localhost:8080/status` — check a claim with its confirmation number and email
- `http://localhost:8080/admin/claims` — staff queue (log in as `admin` with the
  password you put in `.env`)

On the first start, the app creates one **supervisor** account from `ADMIN_USERNAME` /
`ADMIN_PASSWORD`. Log in with it and add caseworkers from the **Staff** page. There is no
built-in default password: with no staff accounts and no `ADMIN_PASSWORD`, the app
refuses to start.

**Upgrading from an earlier version of this project?** Older versions stored claim
statuses in a MySQL `ENUM` column, which can't hold the new `APPEALED` status. Reset the
local database once with `docker compose down -v` (this deletes local test data), then
`docker compose up --build`.

## Running the tests

```bash
mvn test
```

Tests run against an in-memory H2 database (see `src/test/resources/application-test.yml`),
so they don't need MySQL or Docker running. There are 87 tests. They cover the
rules, not just "does it start":

- `ClaimServiceTest`, `PaymentCalculatorTest`: eligibility, benefit amount, and each weekly
  payment rule (plain unit tests, no Spring).
- `ClaimRulesTest`, `CertificationServiceTest`: status moves, appeal deadlines, separation
  of duties, waiting weeks, and the payment ledger. These use a test clock
  (`MutableClock`), so date rules are tested without waiting for real days.
- `FraudCheckServiceTest`, `DocumentServiceTest`, `AuditHistoryTest`: flags, upload
  safety (file type from magic bytes, size limits, path tricks), and history.
- `*PagesTest`, `ClaimantFlowTest`, `StaffAccessTest`: real HTTP requests through Spring
  Security and the templates, including logins, roles, CSRF, and file uploads.

## Deploying to AWS

This is written but **not applied** by default — it costs money to run and needs your
own AWS account.

```bash
cd terraform
terraform init
cp terraform.tfvars.example terraform.tfvars   # then fill in real passwords
terraform plan
terraform apply
```

This provisions, inside your account's **default VPC**:

- One EC2 instance (`t3.micro`, free-tier eligible) that clones this repo, builds the
  Docker image, and runs it — see `terraform/user_data.sh.tpl`.
- One RDS MySQL instance (`db.t3.micro`, free-tier eligible for 12 months) that is
  **not publicly accessible** — only the app's security group can reach it on 3306.
- One S3 bucket for uploaded documents: all public access blocked, encrypted at rest,
  and versioned.
- An IAM role for the EC2 instance that can only read and write that one bucket. The app
  gets short-lived credentials from the role, so there are no AWS keys anywhere. The
  instance requires IMDSv2.

`terraform apply` prints the app's public IP. Give the instance a minute or two to
finish booting and building the image before it's reachable on port 8080.

**When you're done demoing it, tear it down** so free-tier limits don't turn into a
bill:

```bash
terraform destroy
```

## Known limitations (on purpose)

This is a portfolio project, not a production benefits system — a few corners were cut
deliberately rather than by accident, and it's worth being able to say so:

- **Staff log in with a username and password.** A real agency would use single sign-on
  with MFA through its identity provider.
- **Claimants have no password**: they prove who they are with their confirmation number
  and email. A real system would verify identity (for example with Login.gov or ID.me) and
  rate-limit lookups.
- **Payments are recorded, not sent.** There is no bank transfer, debit card, or tax
  withholding.
- **Fraud checks are simple rules.** Real programs also cross-check employer wage
  records and other agencies' data.
- **Eligibility rules are simplified.** Real state UI systems use base-period wage
  calculations and many more separation rules.
- **The database schema is managed by Hibernate** (`ddl-auto: update`). A real system
  would use versioned migrations (Flyway or Liquibase).
- **The EC2 instance runs a single container directly**, not behind a load balancer or
  auto-scaling group — fine for a demo, not for real traffic.

## What I'd do next

Put the EC2 instance behind an Application Load Balancer with HTTPS and an Auto Scaling
Group, move secrets out of Terraform variables into AWS Secrets Manager, switch the schema
to Flyway migrations, and send claimants email notices when their status changes or a
weekly certification is due.

## License

MIT — see [LICENSE](LICENSE).
