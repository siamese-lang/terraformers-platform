# Case C Portfolio Sufficiency Closure Decision

## Status

**USER-APPROVED DECISION — PORTFOLIO CLOSURE IMPLEMENTATION**

Approved on 2026-10-02.

## Decision driver

The repository success criterion is a small number of technically defensible engineering cases, not
a production system with every possible hardening item completed.

Case C originally served the **cloud infrastructure / operations** role beside:

- Case A — AI/RAG;
- Case B — backend durability;
- Case C — GCP runtime architecture, delivery, persistence and operational validation.

That role must remain explicit. The case is therefore closed around the infrastructure decisions and
live evidence already obtained, while unfinished capacity and rollback work remain residual/deferred.

## Selected portfolio case

Case C is:

> **GCP Production-Representative Runtime & Immutable Delivery**

Observability is a supporting capability, not a fourth representative case.

The primary engineering claim is:

> The project moved from an evaluation-oriented target to a production-representative GCP runtime
> with separated database/storage/secret/artifact boundaries, keyless GitHub delivery, immutable
> source-to-image provenance, persistent state across backend replacement, and repository-owned
> metrics/log correlation sufficient to validate the runtime and classify failures. Capacity and
> rollout hardening were stopped when the workload correctness prerequisite became unstable rather
> than manufacturing an unsupported infrastructure conclusion.

## Infrastructure evidence already obtained

### 1. Runtime topology and hosting decision

The selected target separates responsibilities:

- GKE Standard — Spring backend and OpenSearch;
- Vertex AI — fact extraction, embeddings and Terraform generation;
- dedicated Compute Engine VM — MariaDB 11.4;
- dedicated Persistent Disk — MariaDB data authority;
- Cloud Storage — source/result object bytes;
- Secret Manager + native GKE Secret Sync — runtime secret delivery;
- Artifact Registry — immutable backend images.

MariaDB on the current GKE worker was rejected for the initial representative runtime because DB
pressure would be coupled to backend/OpenSearch capacity and weaken bottleneck attribution.

Cloud SQL for MySQL was not selected as a drop-in replacement because the unchanged Flyway migration
contract failed against MySQL 8.4 while MariaDB 11.4 remained compatible. An intentional database
migration would have expanded Case C into a different project.

### 2. Protected GCP foundation and secret boundary

Retained live evidence includes:

- runtime-dependency apply run `36851221194`: exact MariaDB image, private runtime GCS bucket,
  MariaDB VM/PD/firewall/service account, Secret Manager access boundary and native Secret Sync;
- Kubernetes prerequisite run `36854218346`: synchronized secret key, internal JWKS runtime,
  MariaDB Service/EndpointSlice and no accidental backend deployment.

The database service remained private; no public MariaDB ingress was selected.

### 3. Integrated production-representative path

Run `36889896239` is retained as **PASS** evidence for:

- authenticated upload;
- durable AnalysisJob acceptance;
- real Vertex fact extraction and generation;
- real Vertex embedding;
- OpenSearch retrieval;
- GCS source/result persistence;
- terminal `SUCCEEDED`;
- generated Terraform draft read-back.

This is the representative service path that Case A's evaluation-only pod did not prove.

### 4. Persistence across backend replacement

Run `36891629279` is retained as **PASS** evidence for:

- backend pod replacement;
- immutable backend image/source identity preservation;
- durable AnalysisJob identity preservation;
- source GCS bytes preservation;
- result GCS bytes preservation;
- Terraform draft bytes preservation;
- Actuator/Prometheus reachability before and after replacement.

This is the core safe-runtime evidence for the portfolio claim. It does not claim database HA or
zero-downtime multi-replica delivery.

### 5. Keyless and immutable delivery

The delivery path separates infrastructure mutation from image publication:

- GitHub OIDC / Workload Identity Federation rather than a user-managed long-lived GCP key;
- dedicated image publisher identity;
- repository-scoped Artifact Registry writer/reader boundaries;
- source-SHA image identity;
- no mutable `latest` publication.

The later reviewed image publication run `37015159218` built exact source
`b420291fa1534184d2a260883df718cc96511505` and resolved:

`sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`

Run `37015932695` then:

- verified the previous deployed digest before mutation;
- rolled out the exact new digest;
- reached Ready replicas = 1 and Available replicas = 1;
- verified the embedded source revision;
- reported backend health UP.

## Observability support

The project already has enough repository-owned observability to support the three cases without
creating a separate monitoring project:

- Spring Boot Actuator and Prometheus export;
- `terraformers.analysis.jobs{outcome}`;
- `terraformers.analysis.failures{category}`;
- `terraformers.analysis.duration`;
- `terraformers.analysis.stage.duration{stage,outcome}`;
- `terraformers.analysis.stage.failures{stage,category}`;
- durable-job claim, dispatch, queue-wait, retry, recovery and cleanup signals;
- executor rejection counter;
- `analysisJobId` in MDC for job-level correlation;
- `BUILD_SOURCE_REVISION` in logs for release/source correlation.

Metric dimensions are intentionally bounded. Job IDs, prompts, object keys and raw diagnostic
messages are not used as metric labels.

The project does **not** claim a completed distributed-tracing/monitoring platform. Grafana,
OpenTelemetry Collector, Jaeger/Tempo, Cloud Trace and arbitrary SLO/alert thresholds are not needed
to make the current portfolio claims.

## Capacity attempt as an operational stopping decision

Capacity run `36957682821` stopped at concurrency 1 before a valid saturation result because the
integrated path experienced Terraform executable-correctness failure.

The project did not reinterpret this as GKE/CPU/OpenSearch saturation.

The validation boundary was audited, false repository-owned policy failures were removed in PR #190,
and the same path was rerun using an exact immutable revision.

C2 run `37016993776` then:

- passed attempt 1;
- failed attempt 2 with terminal `terraform_validate_configuration`;
- preserved the exact runtime/source/image/fixture identity;
- stopped at 1/5;
- did not automatically rerun the whole gate.

This result is retained only as evidence that **capacity attribution must stop when the workload's
functional correctness prerequisite is unstable**. It is not the primary Case C topic and does not
turn Case C into another AI/RAG case.

## Portfolio closure acceptance

Case C is portfolio-sufficient because the repository can explain:

1. why an evaluation-only environment was insufficient for infrastructure/operations claims;
2. why MariaDB was separated onto a dedicated VM/PD rather than placed in the measured GKE worker;
3. why MySQL/Cloud SQL was deferred after concrete migration incompatibility;
4. how private persistence, secret delivery and immutable artifact delivery were assembled;
5. how GitHub-to-GCP keyless identities were separated by responsibility;
6. how exact source SHA → image digest → deployed revision identity was verified;
7. how the authenticated integrated path and backend-replacement persistence were proven live;
8. how bounded metrics/log correlation supported runtime validation and failure classification;
9. why capacity testing was stopped rather than misattributing correctness variance to infrastructure;
10. which production-hardening risks remain explicitly unsolved.

## Deferred production hardening

The following remain valid future engineering work but are not portfolio prerequisites:

- another C2 rerun solely to obtain 5/5;
- finer Terraform validation diagnostics solely to chase another generated draft;
- executor-aware saturation-harness repair;
- a complete saturation curve and capacity tuning;
- HPA/replica/node/OpenSearch optimization;
- repository/live image desired-state convergence;
- multi-replica zero-downtime rollout optimization;
- integrated-path Kubernetes readiness redesign;
- faulty-release rollback experiment;
- MariaDB HA/replication/failover;
- a full tracing/dashboard/alerting platform.

They may be reopened for a real operational requirement, a reproduced defect, or an explicit future
portfolio-hardening decision.

## Case A / Case B reopening rule

This closure does **not** reopen Case A or Case B.

Case A's accepted claim is retrieval grounding/generalization and negative-control behavior. It
already excludes deployment-correct Terraform as a claim.

Case B's accepted claim is durable AnalysisJob ownership, fencing, bounded retry, restart recovery
and result accountability.

A closed case is reopened only when new evidence directly contradicts one of its material claims or
its operating requirement changes.

## Non-claims

Case C closure does not claim:

- production capacity or saturation limits;
- an optimized GKE/OpenSearch/backend shape;
- 5/5 generated Terraform reliability;
- zero-downtime rollout;
- completed faulty-release rollback;
- MariaDB HA;
- a full observability/tracing platform;
- fully converged declarative/live image state;
- unattended production Terraform apply safety.
