# Case C Final Portfolio Closure — GCP Production-Representative Runtime & Immutable Delivery

## Status

**PORTFOLIO-CLOSED — RESIDUAL RISKS ACCEPTED**

Closure decision:
[Case C Portfolio Sufficiency Closure Decision](../plans/active/case-c-portfolio-sufficiency-decision.md).

## Portfolio role

Case C is the project's **cloud infrastructure / operations** representative case.

Case A covers AI/RAG retrieval and evaluation. Case B covers backend durable processing. Case C
covers the infrastructure boundary required to run that application as a representative GCP service:

`GitHub delivery → Artifact Registry → GKE backend/OpenSearch → Vertex AI → MariaDB VM/PD → GCS`

with Secret Manager/Secret Sync, Workload Identity and repository-owned observability supporting the
runtime.

## Initial limitation

The early GCP target was sufficient for AI evaluation but not for an infrastructure portfolio claim.
It did not initially prove the full authenticated backend path, durable relational/object
persistence, or immutable backend delivery.

Running capacity tests against an evaluation pod would have omitted:

- authenticated API acceptance;
- durable AnalysisJob queue/ownership behavior;
- backend executor behavior;
- MariaDB state;
- source/result object persistence;
- release identity and backend replacement behavior.

The project therefore chose to assemble the smallest production-representative runtime first.

## Architecture decisions

### Database hosting

Three realistic directions were considered.

**Cloud SQL / MySQL**

Deferred. MySQL 8.4 connected and applied earlier Flyway migrations but rejected the retained V003
migration syntax. Treating Cloud SQL as a drop-in replacement would therefore require an intentional
database migration and revalidation of Case B semantics.

**MariaDB inside the measured GKE cluster**

Rejected for the first representative runtime. Database CPU/memory/I/O pressure would share the same
worker boundary as backend/OpenSearch and make later bottleneck attribution weaker.

**Dedicated Compute Engine VM running MariaDB 11.4**

Selected. It preserved the proven MariaDB contract while separating database resource pressure from
the GKE application worker. MariaDB data is backed by a dedicated Persistent Disk rather than the
container writable layer.

This is not a claim of database HA.

### Object persistence

Cloud Storage implements the provider-neutral source/result object boundary. The selected runtime
keeps source/result bytes independent of backend pod lifecycle.

### Secret delivery

Secret Manager holds runtime secret containers and GKE native Secret Sync supplies the application
secret into Kubernetes. Secret payloads are not Terraform-managed values.

### Artifact delivery

Artifact Registry provides the immutable backend image boundary. GitHub image publishing uses a
dedicated short-lived WIF identity rather than a user-managed service-account key.

Infrastructure mutation and image publication remain distinct trust responsibilities.

## Live infrastructure evidence

### Runtime dependencies

Run `36851221194` applied the reviewed dependency surface including:

- private runtime GCS bucket;
- MariaDB service account;
- secret-access boundary;
- dedicated MariaDB Persistent Disk;
- dedicated MariaDB Compute Engine VM;
- private database firewall boundary;
- native GKE Secret Sync enablement.

The database image was pinned to an exact MariaDB 11.4 digest.

### Kubernetes prerequisites

Run `36854218346` proved:

- synchronized DB password key exists without exposing the value;
- internal JWKS runtime is available;
- MariaDB Service/EndpointSlice is configured;
- the backend Deployment was not accidentally introduced by the prerequisite step.

## Full integrated path evidence

Run `36889896239` passed the representative application path:

`authenticated upload → durable AnalysisJob → Vertex fact extraction/generation → Vertex embedding
→ OpenSearch retrieval → GCS source/result persistence → terminal success → Terraform read-back`

This matters because Case A's bounded evaluation environment did not claim this complete
infrastructure/application path.

## Backend-replacement durability evidence

Run `36891629279` passed the replacement scenario:

- backend pod replaced;
- immutable backend image/source identity preserved;
- durable AnalysisJob identity remained readable;
- source GCS bytes remained readable;
- result GCS bytes remained readable;
- generated Terraform draft bytes remained readable;
- Actuator/Prometheus remained reachable before and after replacement.

This demonstrates that application pod replacement does not own the durable database/object result
state used by the portfolio scenario.

It does not prove multi-replica zero-downtime availability or database failover.

## Immutable delivery evidence

The delivery path uses exact revision identity instead of a mutable release label.

Image publication run `37015159218` built source:

`b420291fa1534184d2a260883df718cc96511505`

and published digest:

`sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`

The workflow:

- verified `BUILD_SOURCE_REVISION`;
- published no mutable `latest` tag;
- resolved the remote digest.

Rollout run `37015932695` then:

- verified the expected previous deployed digest before mutation;
- deployed the exact target digest;
- reached Ready replicas = 1;
- reached Available replicas = 1;
- verified the embedded source revision;
- reported backend health UP.

This creates a defensible Git commit → image → deployed revision provenance chain.

## Observability as supporting infrastructure

Observability is deliberately a dependency of A/B/C, not a separate fourth case.

Existing repository-owned signals include:

- Actuator + Prometheus export;
- analysis job started/succeeded/failed counters;
- bounded failure categories;
- total analysis duration;
- provider-neutral stage duration and stage failure categories;
- queue wait;
- claim/dispatch/retry/recovery/cleanup metrics;
- executor rejection;
- `analysisJobId` MDC correlation;
- source revision in logs.

This split is intentional:

- job-specific identity belongs in logs/MDC;
- metrics use bounded dimensions such as stage/outcome/category;
- prompts, object keys, raw exception messages and job IDs are not metric labels.

The repository does not claim that `trace_id` / `span_id` are backed by a completed distributed
tracing system, and it does not claim Grafana/OpenTelemetry/Cloud Trace/SLO alerting was completed.

The existing signals are sufficient for the portfolio evidence because they supported concrete
runtime and failure classification rather than serving as decorative dashboards.

## Capacity experiment and stopping rule

After the representative runtime existed, the project attempted the capacity baseline.

Run `36957682821` stopped at concurrency 1 before a valid saturation point because the integrated
workload experienced executable-Terraform correctness failure.

No CPU/OpenSearch/executor saturation conclusion was fabricated from that run.

The validation ownership was audited and false repository-owned policy rejection was removed. A
later exact immutable revision was deployed and the bounded correctness gate was repeated.

Run `37016993776`:

- used the frozen source/image/fixture/runtime identity;
- passed attempt 1;
- failed attempt 2 with terminal `terraform_validate_configuration`;
- stopped at 1/5;
- did not automatically rerun the gate.

The infrastructure conclusion is therefore narrow:

> Do not tune GKE/runtime capacity against a workload whose correctness prerequisite is still
> variable.

This is a stopping decision, not the main Case C accomplishment.

## Technical judgment demonstrated

Case C can now explain:

- why the evaluation-only target was not sufficient for infrastructure evidence;
- why the database was isolated from the measured GKE worker;
- why a managed MySQL migration was not forced after compatibility failure;
- how private persistence and secret delivery were separated from pod lifecycle;
- why GitHub infrastructure apply and image-publish identities were separated;
- how immutable source/digest provenance prevents ambiguous release identity;
- how backend replacement was tested against durable DB/object state;
- how low-cardinality metrics and job-correlated logs supported operational validation;
- why a contaminated capacity experiment was stopped instead of tuned until a desired graph
  appeared.

## Residual risks / deferred production hardening

Not claimed as solved:

- production saturation limit;
- capacity tuning effectiveness;
- HPA/replica/node/OpenSearch optimization;
- zero-downtime multi-replica rollout;
- faulty-release rollback;
- integrated-path readiness/canary semantics;
- MariaDB HA/automatic failover;
- full tracing/dashboard/alerting platform;
- declarative/live image convergence;
- 5/5 generated Terraform reliability;
- unattended production Terraform apply.

These are future hardening items, not hidden failures.

## Case A compatibility

Case A remains closed.

Its material claim is retrieval grounding/generalization and negative-control behavior. It explicitly
does not claim that every generated Terraform result is deployment-correct.

The stricter Case C executable-validity evidence therefore does not invalidate the Case A retrieval
result.

## Portfolio-ready summary

> AI evaluation alone was not sufficient to make an infrastructure claim, so I assembled a
> production-representative GCP path using GKE, Vertex/OpenSearch, a dedicated MariaDB VM with
> Persistent Disk, GCS, Secret Manager/Secret Sync and Artifact Registry. I separated GitHub
> infrastructure and image-publishing identities with WIF, pinned release identity from Git commit
> to image digest, and verified the authenticated integrated path plus persistence across backend pod
> replacement. Actuator/Prometheus metrics and job-correlated logs were used to validate the runtime
> and classify failures without high-cardinality metric labels. When a capacity experiment later
> failed on workload correctness before saturation, I stopped the experiment rather than attributing
> the failure to GKE capacity. The remaining saturation, HA and rollback work is recorded as explicit
> production hardening rather than being presented as completed.
