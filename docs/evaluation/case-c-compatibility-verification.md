# Case C Compatibility Verification

## Status

**COMPLETE — GCS PASS / ARTIFACT REGISTRY PASS / MYSQL 8.4 FAIL**

Execution base:

`03a4ab209d62ad8c5e3aecd275ddb3ff1e264a53`

Work Package:

`.agents/work-packages/case-c-compatibility-verification-v1.yml`

No Cloud SQL instance, GCS application bucket, Artifact Registry repository, service account,
IAM binding, Kubernetes resource, live GKE node, live Vertex call or load test is created by this
verification.

## Gate 1 — MySQL 8.4 relational compatibility

### Current status

**FAIL — SQL MIGRATION INCOMPATIBILITY CONFIRMED AFTER ONE BOUNDED DRIVER/AUTH REPAIR**

The repository already has an authoritative real-MariaDB verification path:

- GitHub workflow: `.github/workflows/backend-local-verification.yml`
- real-DB script: `scripts/checks/mariadb-schema-validation.sh`
- canonical repository test:
  `backend/src/test/java/com/terraformers/modernization/verification/MariaDbRepositorySmokeTest.java`

The Work Package reuses those assets rather than creating a second harness.

A new job uses a clean `mysql:8.4` service and the existing MariaDB Connector/J JDBC URL. Only
the test job overrides Hibernate to `org.hibernate.dialect.MySQLDialect`; production
`application-prod.yml` and all Flyway migrations remain unchanged.

Required result:

- Flyway migrations 001–006 from empty schema;
- prod-profile application startup;
- Hibernate schema validation;
- canonical CRUD/repository smoke;
- concurrent single-winner initial claim;
- concurrent single-winner expired-lease reclaim;
- result-intent ownership/fencing;
- pending-cleanup discovery;
- authoritative MariaDB 11.4 job remains green.

Cloud SQL for MySQL is not selected until this job passes.

### First execution result

Workflow run `36816910868` reached the MySQL 8.4 service successfully, while the authoritative
MariaDB 11.4 job and backend local smoke both passed.

The MySQL job failed **before Flyway executed**:

`RSA public key is not available client side (option serverRsaPublicKeyFile not set)`

The stack trace came from MariaDB Connector/J's `CachingSha2PasswordPlugin` while connecting to
MySQL 8.4's default authentication mechanism. This is classified as **DRIVER/AUTHENTICATION
COMPATIBILITY**, not SQL/migration/transaction incompatibility.

The Work Package permits one bounded repair within the same failure class. That repair is now used:

- add MySQL Connector/J alongside the retained MariaDB Connector/J;
- MySQL verification job only uses `jdbc:mysql:`;
- MySQL verification job only overrides the datasource driver to
  `com.mysql.cj.jdbc.Driver`;
- MySQL verification job retains the test-only `MySQLDialect` override;
- production `application-prod.yml`, MariaDB URL/dialect, migrations and repository code remain
  unchanged.

No further automatic repair is authorized. The second execution has now failed in a different,
material failure class, so the Work Package stops here.

### Second execution result

Workflow run `36817194503` used MySQL Connector/J and `jdbc:mysql:`. Connection succeeded and
Flyway recognized the server as **MySQL 8.4**.

Observed sequence:

- migration `20260714.001`: applied;
- migration `20260714.002`: applied;
- migration `20260714.003`: **FAILED**;
- SQL state: `42000`;
- error code: `1064`.

Failing statement:

`ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS ...`

The MySQL 8.4 reference grammar for `ALTER TABLE ... ADD COLUMN` does not include
`IF NOT EXISTS` for column addition. The repository migration is valid for the retained MariaDB
baseline but is not accepted unchanged by MySQL 8.4.

This is therefore **SQL/MIGRATION COMPATIBILITY**, not CI/tooling noise and not a driver problem.

The frozen gate requires the existing migration set and repository semantics to pass unchanged.
Making Cloud SQL for MySQL pass would require changing an existing production migration or adding a
database-specific migration strategy. That is outside this Work Package and satisfies its FAIL rule.

The temporary MySQL validation job, log-label parameterization and MySQL Connector/J dependency were
removed again after evidence capture so main is not left with an intentionally failing compatibility
job or an unused runtime dependency.

Authoritative execution evidence:

- first run: `36816910868` — driver/authentication failure before Flyway;
- second run: `36817194503` — MySQL connection succeeds; V003 syntax incompatibility reproduced;
- MariaDB 11.4 remained **PASS** in both runs;
- backend local smoke remained **PASS** in both runs.

## Gate 2 — Google Cloud Storage contract compatibility

### Result

**PASS — application contract compatible; live adapter/runtime proof still required later**

Repository contract inspected:

- `ObjectReader`
- `ObjectWriter`
- `ObjectRemover`
- `ObjectStorageException`
- `AwsS3ObjectReader`
- `AwsS3ObjectWriter`
- `FileSystemObjectStore`
- `ProjectArtifactService`

The domain boundary is bucket/key plus bytes and portable metadata. No S3 request/response type is
exposed through the application contract.

| Required behavior | GCS mapping | Result |
|---|---|---|
| binary write preserves bucket/key | `BlobId.of(bucket, key)` + `Storage.create` | PASS |
| text write preserves bucket/key | encode text to bytes + same create path | PASS |
| metadata content type/length/last-modified | GCS `Blob` metadata | PASS |
| exact byte read | Java Storage/Blob content read | PASS |
| overwrite same deterministic key | create same object name; overwrite requires create+delete permissions | PASS |
| delete existing object | `Storage.delete(BlobId)` | PASS |
| absent delete is application-idempotent | Java API returns `false` when blob is absent; adapter can treat both true/false as success | PASS |
| not-found read mapping | `Storage.get(BlobId)` returns null when absent; adapter can map to `ObjectStorageException.NOT_FOUND` | PASS |
| upstream-failure mapping | `StorageException` can map to `ObjectStorageException.UPSTREAM_FAILURE` | PASS |
| provider metadata does not replace application checksum | generated Terraform SHA-256 is computed independently by application | PASS |
| ADC without long-lived key | Google Cloud Java client libraries use Application Default Credentials | PASS |
| GKE workload identity compatible | Google Cloud client libraries can consume workload credentials via ADC | PASS |
| bucket-scoped create/read/update/delete | `roles/storage.objectUser` is grantable at bucket level | PASS |

Important boundary:

- this PASS proves **contract compatibility**, not that a GCS adapter has already been implemented;
- no GCS library or `gcs` runtime selector exists in production code yet;
- no application bucket or bucket IAM exists yet;
- provider eTag semantics must remain metadata-only; application checksum remains the integrity
  authority where equality matters.

A later implementation unit must add deterministic adapter tests and a separately approved live
GKE→GCS round trip before production runtime readiness can pass.

Authoritative references:

- https://docs.cloud.google.com/storage/docs/reference/libraries
- https://docs.cloud.google.com/java/docs/reference/google-cloud-storage/latest/com.google.cloud.storage.Storage
- https://docs.cloud.google.com/storage/docs/access-control/iam-roles
- https://docs.cloud.google.com/storage/docs/access-control/iam-permissions

## Gate 3 — Artifact Registry delivery identity compatibility

### Result

**PASS — least-privilege identity design is compatible; no live registry/IAM proof yet**

Current repository identity evidence:

- GitHub Actions already uses short-lived Google Workload Identity Federation;
- Terraform plan and apply identities are intentionally separate;
- the target GKE node pool already has a dedicated node service account:
  `terraformers-gke-nodes`;
- the existing Workload Identity provider is repository/main restricted;
- `backend/Dockerfile` accepts `BUILD_SOURCE_REVISION` for source identity.

Selected compatible identity shape:

| Required identity property | Proposed mapping | Result |
|---|---|---|
| publisher is not Terraform plan identity | dedicated `terraformers-image-publish` service account | PASS |
| publisher is not broad Terraform apply reuse | dedicated publisher has no runtime mutation role | PASS |
| short-lived GitHub authentication | existing GitHub OIDC/WIF provider + publisher impersonation | PASS |
| no JSON service-account key | WIF only | PASS |
| no PAT / long-lived registry credential | OAuth access token derived from WIF | PASS |
| repository-scoped writer | `roles/artifactregistry.writer` on one Docker repository | PASS |
| GKE pull identity read-only | `roles/artifactregistry.reader` on same repository to `terraformers-gke-nodes` | PASS |
| full source SHA at build | `BUILD_SOURCE_REVISION=<full SHA>` | PASS |
| resolved immutable digest retained | record pushed image `sha256` digest in delivery evidence | PASS |
| Kubernetes deploys digest | use `...@sha256:<digest>`, not mutable tag | PASS |
| publisher cannot mutate GKE/Compute/IAM | no project-level runtime roles required | PASS |
| publisher cannot mutate unrelated repositories | writer binding scoped to one repository | PASS |

Google Cloud documentation explicitly supports:

- GitHub OIDC deployment pipelines through Workload Identity Federation;
- dedicated service accounts for separate deployment pipelines;
- repository-level Artifact Registry Writer for push;
- repository-level Artifact Registry Reader for pull;
- GKE node service account as the private image pull identity.

This means Artifact Registry can fit the existing role-separation model **without granting image
publishing to `terraformers-plan` or `terraformers-apply`**.

Important boundary:

- Artifact Registry API/repository do not exist in the current target Terraform;
- `terraformers-image-publish` does not exist yet;
- no IAM binding is changed by this verification;
- repository creation/bootstrap permissions are a separate reviewed infrastructure action;
- the later implementation must prove actual WIF push, node pull, digest capture and digest-pinned
  deployment before safe-delivery readiness passes.

Authoritative references:

- https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines
- https://docs.cloud.google.com/iam/docs/best-practices-for-using-service-accounts-in-deployment-pipelines
- https://docs.cloud.google.com/artifact-registry/docs/docker/pushing-and-pulling
- https://docs.cloud.google.com/kubernetes-engine/security/configure-node-service-accounts
- https://docs.cloud.google.com/iam/docs/roles-permissions/artifactregistry

## Aggregate result

| Gate | Current result |
|---|---|
| MySQL 8.4 relational compatibility | **FAIL** |
| Cloud Storage application contract | **PASS** |
| Artifact Registry least-privilege identity | **PASS** |

Alternative C as originally defined is **NOT SELECTABLE** under the frozen 3/3 rule.

The failure is narrow: Cloud Storage and Artifact Registry remain compatible candidates, while
Cloud SQL for MySQL cannot be treated as a drop-in relational replacement for the current MariaDB
contract.

The next Case C decision must therefore reopen only the **relational hosting** part of the runtime.
It should compare the cost/scope of intentionally migrating the persistence contract to MySQL against
retaining MariaDB in a production-representative hosting shape. It must not discard the two passing
managed-service compatibility results or silently rewrite migration history.
