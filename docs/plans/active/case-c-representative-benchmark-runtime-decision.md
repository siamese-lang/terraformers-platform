# Case C Production-Representative Runtime Decision Gate

## Status

**COMPATIBILITY VERIFICATION COMPLETE — GCS PASS / ARTIFACT REGISTRY PASS / MYSQL 8.4 FAIL**

Decision source:

- measurement contract: `docs/plans/active/portfolio-case-measurement-contract.md`
- readiness audit: `docs/evaluation/case-c-measurement-readiness-audit.md`
- audited main: `1086165ae527a9e87ba3bf0a9b66c18e234b1275`

This document replaces the earlier benchmark-only selection on this PR.

The user-facing objective is stronger than merely obtaining a runnable benchmark: Case C should
produce capacity and safe-delivery evidence in a backend environment close enough to realistic cloud
operation that the observed bottleneck and rollout behavior are technically defensible in a
portfolio discussion.

That makes a production-representative GCP backend runtime the preferred direction, but **individual
cloud products are not selected merely because they are GCP-native**. Database, object storage and
image delivery must each pass a compatibility gate against the current repository contract first.

No cloud resource creation, image publication, Kubernetes apply, load generation, rollout, rollback,
tuning, IAM change or live Vertex call is authorized by this document.

## 1. Operating scenario

Case C must measure the real integrated service path under representative concurrent requests:

`authenticated API → durable AnalysisJob → dispatcher/worker → Vertex/OpenSearch → result persistence → terminal state`

The same runtime must later support:

- a healthy immutable backend revision rollout while requests/jobs are active;
- one deterministic revision that cannot become ready;
- rollback to the last healthy revision;
- verification that already accepted durable work is neither silently lost nor corrupted.

The environment should be production-representative at the backend/dependency boundary without
forcing unrelated frontend/public-access work into the case.

## 2. Confirmed current gap

The live GCP target already proves:

- one GKE Standard target foundation;
- Workload Identity Federation for GKE;
- Vertex generation and embedding;
- in-cluster OpenSearch;
- evaluation Java execution.

It does **not** yet provide the full backend service path. Repository evidence confirms:

- the GCP overlay intentionally did not apply the full backend Deployment because database,
  object-storage and JWT dependencies were outside the M3 live boundary;
- the portable MariaDB fixture uses `emptyDir`;
- the filesystem object-store fixture uses backend-local `/tmp`;
- the target backend image remains a registry placeholder.

Therefore a Case C load result does not yet have a representative system under test.

## 3. Why the earlier benchmark-only selection is withdrawn

The earlier proposal kept MariaDB and filesystem object storage inside the benchmark environment
using persistent disks.

That would have made the test executable, but it has an attribution problem:

- backend CPU/memory;
- OpenSearch CPU/memory;
- MariaDB CPU/I/O;
- filesystem/PVC I/O

could all compete inside the same small GKE runtime.

A bottleneck found under that shape could therefore reflect the benchmark fixture topology rather
than the operational shape of a cloud service.

For portfolio-quality capacity evidence, the preferred direction is now to move dependencies that
have a compatible managed GCP representation out of the backend node **only after compatibility is
proven**.

## 4. Candidate runtime shape

The preferred **candidate**, subject to the gates below, is:

```text
Internal authenticated load driver
              |
              v
        GKE Spring Backend
          |      |      \
          |      |       \--> Vertex AI
          |      |
          |      +----------> OpenSearch on GKE
          |
          +-----------------> managed relational DB candidate
          |
          +-----------------> managed object storage candidate

GitHub Actions
      |
      v
managed container registry candidate
      |
      v
digest-pinned GKE Deployment
```

The following remain intentionally outside the initial Case C runtime unless later evidence requires
them:

- public HTTPS ingress;
- frontend delivery;
- permanent external IdP;
- multi-zone HA;
- HPA;
- replicas > 1;
- new observability platform.

Internal deterministic JWT/JWKS authentication remains acceptable for the first backend capacity
baseline because authentication correctness, not IdP capacity, is the Case C question.

## 5. Candidate alternatives

### Alternative 0 — load-test the existing evaluation pod

**REJECT.**

It omits API acceptance, durable queue/worker behavior, persistence, accepted-work survival and
Deployment rollout/rollback.

### Alternative A — benchmark-only persistent runtime

Shape:

- GKE backend;
- in-cluster MariaDB on persistent disk;
- filesystem object storage on persistent disk;
- GHCR or another simple image source.

**RETAIN AS FALLBACK, NOT SELECTED.**

It is much better than the evaluation pod, but its DB/storage topology can distort the first
bottleneck and is less representative of real cloud operation.

Use it only if the production-representative candidates below fail compatibility or their required
scope is disproportionate to Case C.

### Alternative C — production-representative backend runtime

Preferred candidate:

- existing GKE backend;
- existing Vertex/OpenSearch path;
- managed relational database **only if current MariaDB/JPA/Flyway/Case B semantics are proven
  compatible**;
- managed object storage **only if the ObjectReader/ObjectWriter/ObjectRemover contract is proven
  compatible**;
- managed immutable image registry **only if it fits the existing GitHub OIDC/GKE identity model
  with bounded least privilege**;
- internal JWT/JWKS and ClusterIP traffic for the first baseline.

**STATUS: PREFERRED CANDIDATE / NOT YET SELECTED.**

### Alternative B — complete the entire GCP application runtime first

**REJECT FOR CASE C.**

Selecting public ingress, frontend delivery, permanent external identity and full production
hardening before measuring the backend would add unrelated architecture and make the capacity case
harder to attribute.

## 6. Compatibility Gate 1 — relational database

### Candidate

**Cloud SQL for MySQL** is a candidate because Cloud SQL does not provide a managed MariaDB engine.

Current Google documentation lists Cloud SQL MySQL 8.4 as the default MySQL major version, with
MySQL 8.0 also supported.

### Repository evidence in favor

The repository is already strongly MySQL-family oriented:

- `flyway-mysql` is already a backend dependency;
- schema uses InnoDB;
- DDL uses MySQL-family constructs such as `AUTO_INCREMENT`, `TIMESTAMP(6)`, index-prefix length,
  `MODIFY COLUMN` and utf8mb4 collations;
- the Case B durable-job implementation uses JPA/JPQL repository updates, pessimistic locking and
  MariaDB transactions rather than a separate MariaDB-specific queue product;
- MariaDB Connector/J documentation states that the driver supports MariaDB and MySQL servers.

MySQL 8.0 release notes record `IF NOT EXISTS` support added in the MySQL 8.0.29 line, so the
repository's `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` migrations are not automatically ruled out
by current MySQL 8.4.

### Repository evidence preventing immediate selection

The production profile is explicitly MariaDB-bound today:

- runtime driver dependency: `org.mariadb.jdbc:mariadb-java-client`;
- Hibernate dialect: `org.hibernate.dialect.MariaDBDialect`;
- existing authoritative persistence verification is against MariaDB;
- no authoritative test has executed all six Flyway migrations plus Case B claim/lease/fencing/
  retry/cleanup behavior on MySQL 8.4.

Therefore "MariaDB and MySQL are similar" is not sufficient evidence.

### Required proof before selection

Run one deterministic **MySQL 8.4 compatibility verification** without Cloud SQL first.

It must prove on a clean MySQL 8.4 instance:

1. Flyway migrations `001` through `006` all apply from empty schema;
2. Spring Boot starts with the MySQL-target database configuration and
   `hibernate.ddl-auto=validate`;
3. existing user/project/file CRUD required by the analysis path works;
4. AnalysisJob create/read works;
5. Case B ownership semantics work:
   - atomic eligible claim;
   - lease renewal;
   - expired-lease reclaim;
   - generation fencing;
   - retry scheduling;
   - result-intent/finalization;
   - pending-cleanup recovery;
6. the existing MariaDB baseline remains green;
7. any required dialect/driver configuration change is explicit and bounded.

If this proof fails because of SQL/dialect/transaction semantics, Cloud SQL for MySQL is **not
selected** and Alternative A or another compatible managed relational option must be reconsidered.

**Current compatibility result: FAIL.** MySQL 8.4 accepts migrations 001–002 but rejects V003 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` with syntax error 1064. Cloud SQL for MySQL is not a drop-in selection under the frozen unchanged-migration gate.

## 7. Compatibility Gate 2 — object storage

### Candidate

**Google Cloud Storage** is the preferred managed-object-storage candidate.

### Repository evidence in favor

The application already depends on provider-neutral contracts:

- `ObjectReader.readMetadata`;
- `ObjectReader.readContent`;
- `ObjectWriter.writeText`;
- `ObjectWriter.writeBytes`;
- `ObjectRemover.remove`.

The stored application identity is logical `bucket + key`; the domain does not require S3 request
types.

The required metadata shape is also portable:

- content type;
- content length;
- provider metadata/eTag field;
- last-modified time.

Generated Terraform checksum integrity is calculated separately in the application, so an object
provider eTag is not treated as the canonical SHA-256 checksum.

Google's Java Cloud Storage client uses Application Default Credentials by default. The current GKE
Workload Identity Federation model can grant a Kubernetes workload principal access directly to a
specific Cloud Storage bucket.

The backend needs create/read/replace/delete object behavior. Google documents
`roles/storage.objectUser` as including create/read/update/delete object access, and replacing an
existing object requires both create and delete permissions.

### Remaining gaps

- no Google Cloud Storage Java dependency exists in `backend/pom.xml`;
- no `gcs` ObjectReader/ObjectWriter/ObjectRemover adapter exists;
- `StorageRuntimeProperties` does not currently accept `gcs`;
- no application data bucket is declared in the GCP target Terraform;
- no bucket-scoped IAM exists for the backend workload principal;
- no authoritative live GCS byte round-trip or idempotent-removal evidence exists.

### Required proof before selection

Before Cloud Storage is selected:

1. demonstrate an adapter mapping that preserves the existing ObjectReader/ObjectWriter/ObjectRemover
   contract without domain/schema changes;
2. deterministic tests must cover binary upload, text result write, metadata read, byte read,
   overwrite of the same deterministic result key, and idempotent delete;
3. confirm the application uses its own checksum where integrity equality is required and does not
   rely on provider eTag semantics;
4. define the minimum bucket-scoped workload IAM required;
5. only after the adapter contract passes may a separately approved bounded live object
   create/read/overwrite/delete round-trip be used to prove GKE Workload Identity access.

**Current compatibility result: PASS.** The application contract maps cleanly to GCS without a domain/schema rewrite. This is not yet a live adapter/runtime proof.

## 8. Compatibility Gate 3 — immutable image registry

### Candidate

**Artifact Registry** is the preferred production-representative image-registry candidate.

### Repository evidence in favor

The repository already has:

- an immutable backend Dockerfile;
- build source revision injection;
- digest-oriented historical release semantics;
- GitHub → GCP Workload Identity Federation;
- separate GCP plan/apply identities;
- one GKE node service account.

GitHub Actions can authenticate to Google Cloud through the existing Workload Identity Federation
pattern without a long-lived service-account key.

Google documents that GKE pulls from Artifact Registry using the node pool's IAM service account and
that this identity needs Artifact Registry Reader access where not already granted.

### Why it is not selected yet

The current identity contract intentionally separates plan and apply responsibilities. Neither
identity should silently become an image-publisher identity.

Current GCP target Terraform also does not:

- enable Artifact Registry API;
- create an Artifact Registry repository;
- grant a dedicated publisher `roles/artifactregistry.writer`;
- grant the GKE node service account repository read access.

Therefore Artifact Registry is compatible in principle, but its least-privilege delivery identity
must be designed before selection.

### Required proof before selection

1. define whether a dedicated image-publish service account or direct WIF principal will publish;
2. prove the publisher cannot mutate unrelated runtime resources;
3. restrict publish permission to the selected repository;
4. grant the existing GKE node service account read-only access to that repository;
5. retain full source SHA + image digest identity;
6. refuse mutable-tag-only deployment;
7. verify no JSON service-account key or long-lived registry credential is required.

If those permissions cannot be kept narrow, compare GHCR as the fallback registry rather than
broadening the Terraform apply identity.

**Current compatibility result: PASS.** A dedicated image-publisher identity can use existing GitHub OIDC/WIF with repository-scoped Writer, while the existing GKE node service account receives repository-scoped Reader. This is not yet a live registry/IAM proof.

## 9. Components already accepted for the candidate runtime

The following do not need to be reopened merely because Alternative C is under review:

- one existing GKE Standard target runtime;
- Vertex `gemini-3.8-flash`;
- `gemini-embedding-001`;
- in-cluster OpenSearch;
- Workload Identity Federation for GKE;
- Spring Boot application;
- Case B durable AnalysisJob ownership semantics;
- one backend replica as the first before-state;
- current backend executor values;
- current OpenSearch resources;
- current Deployment strategy;
- internal ClusterIP service;
- deterministic JWT/JWKS fixture for initial internal benchmark authentication.

These are baseline inputs, not claims that they are already capacity-optimal.

## 10. Baseline invariants if Alternative C is eventually selected

No tuning is allowed before the first valid baseline:

- backend replicas: `1`;
- backend CPU request/limit: `250m / 1 CPU`;
- backend memory request/limit: `512Mi / 1Gi`;
- executor core/max: `2 / 4`;
- executor queue capacity: `50`;
- dispatcher poll interval: `2s`;
- dispatcher batch size: `4`;
- OpenSearch replicas: `1`;
- OpenSearch heap: `1 GiB`;
- OpenSearch CPU request/limit: `1 / 2`;
- OpenSearch memory request/limit: `2Gi / 4Gi`;
- Deployment strategy: `maxUnavailable: 1`, `maxSurge: 0`;
- target node shape: unchanged until a valid pre-baseline scheduling check says otherwise.

If the current node cannot schedule the final representative runtime, record that as a
**pre-baseline capacity blocker**. Do not silently resize and call the resized runtime the original
before-state.

## 11. Decision rule after compatibility verification

Alternative C may be selected only when all three gates are resolved:

| Gate | Required result |
|---|---|
| MySQL 8.4 / relational contract | PASS |
| Cloud Storage application contract | PASS |
| Artifact Registry least-privilege delivery identity | PASS |

Possible outcomes:

- **3/3 PASS** → select Alternative C as the Case C production-representative backend runtime;
- **DB FAIL, storage/registry PASS** → do not force MySQL; reconsider benchmark MariaDB or another
  relational hosting path;
- **storage FAIL** → do not rewrite the domain merely for GCS; reconsider persistent filesystem or
  another adapter-compatible object store;
- **registry IAM FAIL** → compare GHCR fallback before broadening apply/runtime privileges;
- **multiple FAIL** → Alternative A becomes the safer representative fallback.

This prevents cloud-product preference from overriding application compatibility.

## 12. Validation boundary after a final selection

Whichever representative runtime is ultimately selected must prove, before load testing:

- exact immutable backend source/image identity;
- authenticated internal upload request succeeds;
- source bytes persist outside backend pod lifetime;
- MariaDB/MySQL relational state persists outside backend pod lifetime;
- real Vertex/OpenSearch adapters are active;
- one normal analysis reaches terminal `SUCCEEDED`;
- result bytes and relational result identity remain readable after backend pod replacement;
- Case B durable job state remains coherent after backend replacement;
- Actuator/Prometheus metrics are reachable internally;
- no second GKE environment is created.

Only after this runtime-readiness proof may a separate Work Package freeze the load profile,
concurrency steps, duration, collection method and stopping rule.

## 13. Explicit non-decisions

This PR does **not** yet select:

- Cloud SQL for MySQL;
- MySQL as a replacement relational contract;
- Cloud Storage;
- an application data bucket;
- Artifact Registry;
- an image-publisher service account;
- public ingress;
- production external identity;
- frontend hosting;
- Cloud SQL HA;
- GCS retention/versioning policy;
- HPA;
- replicas > 1;
- node sizing changes;
- executor tuning;
- OpenSearch sizing changes;
- capacity thresholds;
- latency SLOs.

## 14. Immediate next single task

Compatibility verification is active under
`.agents/work-packages/case-c-compatibility-verification-v1.yml`.

Completed design gates:

- Cloud Storage application-contract compatibility: **PASS**;
- Artifact Registry least-privilege delivery identity compatibility: **PASS**.

The MySQL 8.4 real-database gate is now **FAIL**. Connection succeeded with MySQL Connector/J and
Flyway applied migrations 001 and 002, but migration 003 failed on MariaDB-specific
`ADD COLUMN IF NOT EXISTS` syntax. The authoritative MariaDB 11.4 baseline remained green.

Therefore Alternative C in its original 3/3 form is **NOT SELECTABLE**. Cloud Storage and Artifact
Registry remain passing candidates; only relational hosting must be reopened.

The next decision must compare:
- intentionally migrating the persistence contract to MySQL/Cloud SQL, including migration-history
  compatibility and Case B semantics; versus
- retaining MariaDB in a production-representative hosting shape.

No live GCP resource creation or IAM mutation is authorized by this completed compatibility Work Package.

## 15. Approval boundary

**Runtime selection status: COMPATIBILITY_VERIFICATION_COMPLETE — 2/3 PASS, MYSQL 8.4 FAIL; RELATIONAL HOSTING DECISION REOPENED**

No implementation Work Package for the production-representative runtime may be created until the
compatibility evidence above is reviewed.

No cloud apply, image push, bucket/Cloud SQL/Artifact Registry creation, IAM grant, Kubernetes apply,
load generation or rollout/rollback experiment is authorized by this PR.

## References

- Cloud SQL for MySQL database versions:
  https://docs.cloud.google.com/sql/docs/mysql/db-versions
- Cloud Storage Java client authentication:
  https://docs.cloud.google.com/storage/docs/reference/libraries
- Workload Identity Federation for GKE:
  https://docs.cloud.google.com/kubernetes-engine/docs/concepts/workload-identity
- Cloud Storage IAM roles:
  https://docs.cloud.google.com/storage/docs/access-control/iam-roles
- Artifact Registry / GKE integration:
  https://docs.cloud.google.com/artifact-registry/docs/integrate-gke
- MariaDB Connector/J server compatibility:
  https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j
