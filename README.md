# Benefits Portal (AWS)

A citizen-facing unemployment-benefits claim portal: claimants file a claim online, the
app calculates a weekly benefit amount on the spot, and an admin reviews and
approves/denies it. Built as a portfolio project targeting government-services /
GovTech internships (Maximus and similar contractors run exactly this kind of system
for state unemployment insurance and benefits programs).

## Why this project

State unemployment-insurance systems are large, old, and slowly being modernized —
which is the actual business a lot of government-technology contractors are in. This
project is a small, honest version of that same shape: a Java/SQL app (the stack these
internships actually list as a requirement) that also gets containerized and deployed
to AWS with infrastructure-as-code, instead of stopping at "runs on my machine."

## What it does

- **Citizens** file a claim with their employer, last day worked, reason for
  separation, and average weekly wage.
- The app immediately calculates **monetary eligibility** and a **weekly benefit
  amount** (50% wage replacement, floored at $50/week, capped at $500/week — all
  configurable). A disqualifying separation reason (fired for misconduct, quit without
  cause) zeroes the benefit out, the same way real state rules work.
- **Admins** log in to a review queue, filter claims by status, and move each one
  through `FILED → UNDER_REVIEW → APPROVED/DENIED` with notes.
- A JSON API sits behind the same service layer as the web pages (`/api/claims`), so
  the business logic isn't tied to one front end.

## Architecture

```
                 ┌────────────────────┐
  Citizen ─────▶ │   Spring Boot app   │ ──▶ MySQL (RDS in prod,
  Admin   ─────▶ │  (Thymeleaf + REST) │      docker-compose locally)
                 └────────────────────┘
                          │
                 Docker image, built by
                 GitHub Actions CI on every push
                          │
                          ▼
              Terraform provisions: EC2 (runs the
              container) + RDS MySQL + security groups,
              inside the AWS account's default VPC
```

## Tech stack

Java 21 · Spring Boot 3 (Web, Data JPA, Security, Thymeleaf, Validation) · MySQL 8 ·
Docker · Terraform (AWS provider) · GitHub Actions

## Running it locally

Requires Docker and Docker Compose.

```bash
git clone https://github.com/sayyednx/benefits-portal-aws.git
cd benefits-portal-aws
docker compose up --build
```

Then open:

- `http://localhost:8080/` — file a claim
- `http://localhost:8080/admin/claims` — admin queue (default login `admin` /
  `changeme123` — **change this** via the `ADMIN_PASSWORD` env var before you show
  this to anyone, and definitely before deploying it anywhere public)

## Running the tests

```bash
mvn test
```

Tests run against an in-memory H2 database (see `src/test/resources/application-test.yml`),
so they don't need MySQL or Docker running. They cover the parts of this app that
actually have logic worth testing: the eligibility rule and the benefit calculation
(`ClaimServiceTest`), not just "does it start."

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

- **Auth is a single in-memory admin account.** A real system would use a proper
  identity provider (SSO, MFA) and per-user audit trails, not one shared login.
- **No document upload** (pay stubs, termination letters) — real claims usually need
  supporting evidence.
- **The EC2 instance runs a single container directly**, not behind a load balancer or
  auto-scaling group — fine for a demo, not for real traffic.
- **Eligibility rules are simplified.** Real state UI systems have far more nuance
  (partial benefits, base-period wage calculations, appeals) than the single
  disqualifying-reason check here.

## What I'd do next

Add a document-upload step for supporting evidence, put the EC2 instance behind an
Application Load Balancer with an Auto Scaling Group instead of a single box, move
secrets out of Terraform variables and into AWS Secrets Manager, and add an audit log
of every status change (who changed what, when) — the kind of thing an actual benefits
program would be required to keep.

## License

MIT — see [LICENSE](LICENSE).
